# Buttle README

## What is it?

_Buttle_ is a proxying JDBC driver with hooks.

__proxies__

_Buttle_ ships a `java.sql.Driver` which delegates `connect` calls to
a backing (or _real_) driver (like `org.postgresql.Driver`). The
_Buttle_ driver wraps a _Buttle_ proxy around the returned
`java.sql.Connection`. The _Buttle_ proxy then (recursivly) does the
same to the wrapped instance -- i.e. it wraps a _Buttle_ proxy around
the return value of delegated method calls.

The effect of this is that the application which is using the _Buttle_
`java.sql.Driver` will ever only call JDBC API methods through a
_Buttle_ proxy (e.g. for `java.sql.Statement` and
`java.sql.ResultSet`).

_Buttle_ proxies are only constructed for methods (i.e. their returned
values) that have interface-typed declared return types.

__hooks__

_Buttle_ proxies delegate calls to the backing JDBC driver's instances
through `buttle.proxy/handle` multi method. Via
`buttle.proxy/def-handle` users can _inject/hook_ their own
multi-method implemenations (per interface/method) into the delegation
mechanism. See example in `examples/buttle/examples/open_tracing.clj`
and `examples/buttle/examples/handle.clj`.

__events__

_Buttle_ proxies also create _events_ for every method invocation and
completion incl. when an `Exception` is thrown (see
`buttle.proxy/handle-default`).

These events include info about

* timestamp of the event
* duration (for completion/`Exception`)
* stacktrace (for exceptions)
* invoked class/method
* arguments
* returned value/`Exception`

Events (see `event.clj`) are communicated through a `core.async`
channel/mult so that users can consume `buttle.event/event-mult` to
receive the events. See usage example in
`examples/buttle/examples/event_channel.clj`.

__referencs__

Similar things have been done before:

* https://www.javaspecialists.eu/archive/Issue136.html  
* https://jaxenter.de/jdbc-treiber-selbstgebaut-java-trickkiste-636  
* http://jamonapi.sourceforge.net/jamon_sql.html  
* https://github.com/arthurblake/log4jdbc  
* https://p6spy.readthedocs.io/en/latest/index.html  
* http://sfleiter.github.io/blog/2013/12/08/jboss-datasource-proxy-with-log4jdbc-log4j2/

## What to use it for?

Use it for

* testing

* troubleshooting

* debugging

* performance measurements

* application monitoring

__Note__: I haven't done any measurements on how much _Buttle_ hurts
the performance.

## How to extend?

There are two ways to _hook into Buttle_:

__events__: receive events from _Buttle_ through
  `buttle.event/event-mult` like this:

	(let [ch (clojure.core.async/chan)]
	  (clojure.core.async/tap buttle.event/event-mult ch)
	  (clojure.core.async/go
	   (loop []
		 (when-let [e (clojure.core.async/<! ch)]
		   (println e) ;; do something with the event
		   (recur)))))

__multi method__: _inject_ your own proxy for _target_
  interfaces/methods. This acts like an AOP advice/proxy. Note that in
  this case you have to take care to send events if you need that (see
  `buttle.proxy/handle-default`).

So you can (re-) register the `buttle.proxy/handle :default`.

	(defmethod buttle.proxy/handle :default [the-method target-obj the-args]
	  (do-some-thing-with-call the-method target-obj the-args))

And you can register multi method implementation for just specific
interfaces, methods or a combination of these (see
`test/buttle/proxy_test.clj`,
`examples/buttle/examples/open_tracing.clj` and
`examples/buttle/examples/handle.clj` for more examples):

    (buttle.proxy/def-handle [java.sql.Connection :buttle/getCatalog] [the-method target-obj the-args]
	  (do-some-thing-with-call the-method target-obj the-args))

`do-some-thing-with-call` could look like this:

    (defn invoke-with-logging [the-method target-obj the-args]
      (println (format "buttle.examples.handle: INVOKE %s"
                       (pr-str [the-method target-obj (into [] the-args)])))
      (let [r (try
                (proxy/handle-default the-method target-obj the-args)
                (catch Throwable t
                  (do
                    (println (format "buttle.examples.handle: THROW %s : %s"
                                     (pr-str [the-method target-obj (into [] the-args)]) (pr-str t)))
                    (throw t))))]
        (println (format "buttle.examples.handle: RETURN %s --> %s"
                         (pr-str [the-method target-obj (into [] the-args)]) (pr-str r)))
        r))

## Examples

### SQuirreL

__(1)__ In `squirrel-sql.bat` add system property `buttle.user-file` to java call:

	set BUTTLE="-Dbuttle.user-file=<path-to>/buttle/examples/handle.clj"
	java [...] %BUTTLE% [...]

__(2)__ In the GUI add a _Driver_ with _extra classpath_
  `<path-to>/buttle-standalone.jar` and class `buttle.jdbc.Driver`.

__(3)__ In the GUI add an _Alias_ with URL (replace `<user>` and `<password>`). Text
  following `jdbc:buttle:` will be read as Clojure map form.

	jdbc:buttle:{:user "<user>" :password "<password>" :target-url "jdbc:postgresql://127.0.0.1:6632/postgres"}

### Wildfly

When using _Buttle_ in Wildfly (either _domain_ mode oder
_standalone_) you can either include it in your application (but
usually JDBC drivers are not included in the main app) or you can
deploy it as a _module_ (tested with Wildfly 12.0.0.Final).

For this you have to:

* define a `<module>`
* define a `<driver>`
* define a `<datasource>` (or `<xa-datasource>`; see below)

__(1)__ Define `<module>`: put this into
  `<wildfly-root>/modules/buttle/main/module.xml`. You may have to
  adjust `path` to _Buttle_'s JAR filename. Note that you have to
  include `<dependencies>` for the `javax.api` base module and the
  JDBC driver you want to wrap (Postgres in this case). Otherwise
  _Buttle_ won't be able to _see_ the JDBC driver's classes.

	<?xml version="1.0" encoding="UTF-8"?>
	<module xmlns="urn:jboss:module:1.1" name="buttle">

	  <resources>
		<resource-root path="buttle-standalone.jar"/>
	  </resources>

	  <dependencies>
		<module name="postgres"/>
		<module name="javax.api"/> 
	  </dependencies>

	</module> 

__(2)__ Define `<driver>`: Note that Wildfly does not need to know
  _Buttle_'s driver class (`buttle.jdbc.Driver`). It relies on
  _Buttle_ being loaded via SPI
  (`META-INF/services/java.sql.Driver`). Wildfly then finds the
  _Buttle_ driver via `DriverManager/getConnection`.

    <drivers>
      <driver name="buttle-driver" module="buttle"/>
    </drivers>

__(3)__ Define `<datasource>`: in this example there is no extra
  Postgres `<datasource>`/`<driver>` entry so Wildfly will not load
  the Postgres JDBC driver for us. Therefore we put `:class-for-name
  "org.postgresql.Driver"` into the _Buttle_ URL. Now _Buttle_ loads
  the JDBC driver's class and usually these will register themselves
  with the `DriverManager`. After that _Buttle_ can connect to
  Postgres through the `DriverManager`.

    <datasource jndi-name="java:/jdbc/buttle-ds" pool-name="buttle-ds" use-java-context="true">
        <driver>buttle-driver</driver>
        <connection-url>
	        jdbc:buttle:{
		        :user "<user>"
		        :password "<password>"
		        :class-for-name "org.postgresql.Driver"
		        :target-url "jdbc:postgresql://<host>:<port>/<db-id>"}
	    </connection-url>
    </datasource>

Instead of loading the class explicitly you can just use a class
literal with some arbitrary key in the map -- like this:

        <connection-url>
	        jdbc:buttle:{
		        :user "<user>"
		        :password "<password>"
		        :_ org.postgresql.Driver
		        :target-url "jdbc:postgresql://<host>:<port>/<db-id>"}
	    </connection-url>

If you rather have Wildfly load the Postgres driver you just add a
`<driver>` entry for Postgres. In this case you do not need the
`:class-for-name`/class-literal entry.

    <drivers>
      <driver name="buttle-driver" module="buttle"/>
      <driver name="postgresql" module="postgres"/>
    </drivers>

There is yet another way to make Wildfly load Postgres JDBC
driver. Instead of adding the `<driver>` you can add
`services="import"` to the `module/dependencies`:

	<module name="postgres" services="import"/>

Since _Buttle_ itself doesn't give you much functionality you probably
want to define `buttle.user-file` system property to have _Buttle_
load your _hook code_:

    <system-properties>
      <property name="buttle.user-file" value="<path-to>/buttle-user-file.clj" boot-time="true"/>
    </system-properties>

#### A word on authentication

Note that there are three (or four) places involved when it comes to
authentication regarding the _Buttle_ datasource and the proxied
_real_ datasource (in Wildfly and IBM Webpshere - see below):

* __the _real_ datasource configuration__

* __the _real_ datasource's authentication settings__

* __the _Buttle_ datasource configuration__

#### XA-datasource

You define an `<xa-datasource>` like this (for Postgres):

    <xa-datasource jndi-name="java:/jdbc/postgres-xa" pool-name="postgres-xa">
	  <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
      <driver>postgres-driver</driver>
      <security>
        <user-name>postgres-user</user-name>
        <password>postgres-password</password>
      </security>
      <xa-datasource-property name="Url">jdbc:postgresql://127.0.0.1:6632/postgres</xa-datasource-property>
    </xa-datasource>

You can retrieve this from JNDI like this (done via nREPL into running
Wildfly; build UBERJAR with `lein with-profile +swank,+wildfly
uberjar` to include nrepl and Swank):

	user=> (buttle.util/jndi-lookup "java:/jdbc/postgres-xa")
    #object[org.jboss.as.connector.subsystems.datasources.WildFlyDataSource ,,,]

Note though that Wildfly does __not__ give us a
`javax.sql.XADataSource` but a `javax.sql.DataSource`:

	user=> (->> (buttle.util/jndi-lookup "java:/jdbc/postgres-xa") .getClass .getInterfaces (into []))
	[javax.sql.DataSource java.io.Serializable]

Since there is no (easy) way to implement `javax.sql.XADataSource`
based on a `javax.sql.DataSource` _Buttle_ cannot proxy XA-datasources
retrieved from JNDI for Wildfly.

Others got bitten by this [1, 2] and it probably won't get fixed
[3]. So _Buttle_ only supports __(a)__ wrapping _real_
`javax.sql.XADataSource` implemenations retrieved from JNDI and
__(b)__ __creating__ a JDBC driver's XA-datasource directly.

[1] https://stackoverflow.com/questions/52710666/exception-while-looking-up-xadatasource-using-jndi  
[2] https://groups.google.com/forum/#!msg/ironjacamar-users/rxM1WbINnWI/RIdvEYn_iw4J  
[3] https://issues.jboss.org/browse/JBJCA-657  

__Creating a JDBC driver's XA-datasource directly__

So for Wildfly you define a _Buttle_ XA-datasource and specify the
_real_ XA-datasource by setting the `DelegateSpec` property to a
Clojure map form (to be exact I should say 'a form that evaluates to a
map'; line-breaks are removed so DO NOT use `;` comments other than at
the very end). This map must have `:delegate-class` giving the _real_
XA-datasource's class (note that it IS a class-literal!). Any other
map key/value will be used to set the _real_ XA-datasource's Java-Bean
properties. You have to supply the correct property type through the
map. Overloaded setter-methods are not supported.

    <xa-datasource jndi-name="java:/jdbc/buttle-xa" pool-name="buttle-xa">
      <xa-datasource-class>buttle.jdbc.XADataSource</xa-datasource-class>
      <driver>buttle-driver</driver>
      <security>
        <user-name>postgres-user</user-name>
        <password>postgres-password</password>
      </security>
      <xa-datasource-property name="DelegateSpec">
        {:delegate-class org.postgresql.xa.PGXADataSource
         :url "jdbc:postgresql://127.0.0.1:6632/postgres"}
      </xa-datasource-property>
    </xa-datasource>

### IBM Websphere

IBM Websphere (WAS) supports datasources of type
`javax.sql.XADataSource` and `javax.sql.ConnectionPoolDataSource`
(tested with WAS 9.0.0.8 ND).

For XA-datasource WAS (like Wildfly; see above) does not put a
`javax.sql.XADataSource` into JNDI so _Buttle_ cannot proxy
XA-datasources from JNDI for WAS.

For WAS you have the following options:

* __proxy a JNDI `javax.sql.ConnectionPoolDataSource`__  
  Define the _Buttle_ datasource with
  `buttle.jdbc.ConnectionPoolDataSource` and target the _real_
  datasource by setting `delegateSpec` to its JNDI name.

* __define a `javax.sql.ConnectionPoolDataSource`__  
  Define the _Buttle_ datasource with
  `buttle.jdbc.ConnectionPoolDataSource` and (create) target the
  _real_ CP-datasource by setting `delegateSpec` to the map with
  `:delegate-class <real-jdbc-driver-cp-ds-class>` and the
  CP-datasource's Java-Beans property values.

* __define a `javax.sql.XADataSource`__  
  Define the _Buttle_ datasource with `buttle.jdbc.XADataSource` and
  (create) target the _real_ XA-datasource by setting `delegateSpec`
  to the map with `:delegate-class <real-jdbc-driver-xa-ds-class>` and
  the XA-datasource's Java-Beans property values.


__Define _Buttle_ JDBC provider__

For all options you need to define the _Buttle_ __JDBC provider__
first. You can repeat the following steps to define more than one
provider (e.g. to define one provider for XA-datasources and one for
CP-datasources).

In the WAS Admin Console navigate to __Resources/JDBC/JDBC provider__,
select __scope__ (e.g. your cell/node/server), hit __new__.

__Step 1:__

* select __database type__: `user defined`
* enter __implementation class__: `buttle.jdbc.ConnectionPoolDataSource` or
  `buttle.jdbc.XADataSource` (see above)
* enter __name__ (e.g. `Buttle CP-DS`) and __description__
* hit __next__

__Step 2:__

* enter __classpath__: `<path-to-buttle-standalone.jar>`
* hit __next__

__Step 3:__

* just hit __done__


__Define _Buttle_ datasource__

Now you define one or more datasources. In the WAS Admin Console
navigate to __Resources/JDBC/datasources__, select __scope__
(e.g. your cell/node/server), hit __new__.

__Step 1:__

* enter __name__ (e.g. `buttle_cp_ds`) and __JNDI name__
  (e.g. `jdbc/buttle_cp_ds`)
* hit __next__

__Step 2:__

* select __JDBC provider__: e.g. `Buttle CP-DS` (see above)
* hit __next__

__Step 3:__

* __helper__: do not change default `com.ibm.websphere.rsadapter.GenericDataStoreHelper`
* just hit __next__

__Step 4:__

* leave all authentication selections __empty__. In this case
  authentication must come from _Buttle_ datasource.
* just hit __next__

__Step 5:__

* just hit __next__


__Configure _Buttle_ datasource__

Before you can use/test the _Buttle_ datasource you need to configure
it. Navigate to __Resources/JDBC/datasources__, select __scope__
(e.g. your cell/node/server) and click on the _Buttle_ datasource you
want to configure.

Click on __adjust properties__. If you have not added `delegateSpec`
hit __add__. Otherwise click on `delegateSpec` entry in the table.

Enter/change __value__.

__Example: Postgres__

	{:delegate-class org.postgresql.ds.PGConnectionPoolDataSource
	 :databaseName "<postgres-db-name>"
	 :password "<postgres-password>"
	 :portNumber (int <postgres-port-number>)
	 :serverName "<postgres-hostname>"
	 :user "<postgres-user>"}

Then hit __ok__.

__Test _Buttle_ datasource__

Finally we can test/use the _Buttle_ datasource. Navigate to
__Resources/JDBC/datasources__, select __scope__ (e.g. your
cell/node/server). Select (checkbox) the datasource. Hit __test
connection__. If you have just changed the datasource you may get an
error stating that you have to _synchronize_ the datasource settings
first. Go ahead and click __synchronize__ and then repeat the test.

### Clojure

	C:>java -cp buttle-0.1.0-SNAPSHOT-standalone.jar;postgresql-9.4.1212.jar clojure.main -r
	Clojure 1.8.0
	user=> (use 'buttle.driver-manager)
	;;--> nil
	(-> (get-connection "jdbc:buttle:{:user \"<user>\" :password \"<password>\" :target-url \"jdbc:postgresql://127.0.0.1:6632/postgres?\"}" nil nil)
		.createStatement
		(.executeQuery "select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'")
		(resultset-seq))

### Java

	import java.sql.Connection;
	import java.sql.DriverManager;
	import java.sql.ResultSet;
	import java.sql.Statement;

	public class ButtleTest {

		public static void processEvent(Object e) {
			System.out.println("event : " + e);
		}

		public static void main(String[] args) throws Exception {

			System.setProperty("buttle.user-file", "examples/buttle/examples/java_events.clj");

			String user = System.getProperty("buttle_user");
			String password = System.getProperty("buttle_password");

			String jdbcUrl = "jdbc:postgresql://127.0.0.1:6632/postgres";
			String buttleUrl = String.format("jdbc:buttle:{:user \"%s\" :password \"%s\" :target-url \"%s\"}", user,
					password, jdbcUrl);

			Connection conn = DriverManager.getConnection(buttleUrl, user, password);

			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'");

			for (int cc = rs.getMetaData().getColumnCount(); rs.next();) {
				for (int i = 1; i <= cc; i++) {
					System.out.print(i == 1 ? "" : ",");
					System.out.print(rs.getObject(i));
				}
				System.out.println();
			}
		}
	}

Build to `target/` dir:

	C:\>javac -d target java\ButtleTest.java

And run (adjust paths as needed):

	C:\>java -cp buttle-standalone.jar;postgresql-9.4.1212.jar;target -Dbuttle_user=<user> -Dbuttle_password=<password> ButtleTest

## Tests

You'll need a Postgres at `127.0.0.1:6632` (but see `jdbc-url` in
`test/buttle/core_test.clj`).

__Windows__

	> set buttle_user=<user>
	> set buttle_password=<password>
	> lein test
	
__Linux__

	$ buttle_user=<user> buttle_password=<password> lein test

## Building

Use `lein make-doc` to build documenation to
`resources/public/generated-doc`.

Use `lein uberjar` to build the minimum _Buttle_ UBERJAR. It'll
contain _Buttle_, Clojure and `core.async`. It won't contain the Open
Tracing API and no Jaeger.

Use `lein with-profile +jaeger,+wildfly uberjar` to build an UBERJAR
incl. Open Tracing and Jaeger and suitable for usage in Wildfly.

You can use _Buttle_ as a library (`buttle.proxy` could probably be
used for proxying other APIs like LDAP und JMS) but I usually use it
like you would use a self-contained JDBC driver. If you have problems
using _Buttle_ in Clojure environments then you may have to fix the
`make-` aliases.

## TODOS

* add optional loading of `buttle-user-file.clj` via classloader to
  `driver`. With that you don't even need to set the system property
  `buttle.user-file` to load your own code. You just need a dir that
  the classpath/classloader of your app points to.

* add `:driver-class` option to _Buttle_ JDBC-URL so that _Buttle_
  directly instanciates and uses that driver to delegate to (and not
  indirectly delegate to the driver by calling DriverManager/connect).

* add `:datasource-jndi-name` option to _Buttle_ JDBC-URL so that
  _Buttle_ fetches a `DataSource` (not a `Driver`) from JNDI and
  delegates to that.
