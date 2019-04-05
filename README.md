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

Events (see `event.clj`) are communicated through a
`clojure.core.async/mult` that users can `tap` onto to receive the
events. See usage example in
`examples/buttle/examples/event_channel.clj`.

__references__

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

## Using _Buttle_

* You can download _Buttle_ UBERJAR (`driver`) releases and snapshots
  from Clojars [1] and use it in __non-Clojure__ contexts like you
  would use any other JDBC driver. This UBERJAR includes the Clojure
  core lib and `clojure.core.async`. That's why you should not mix it
  with a classpath that contains a seperate Clojure core lib. You find
  examples for this use-case down below.

* For Clojure projects you can use _Buttle_ as a lib/dependency
  (e.g. via Leiningen). Just put the following in your `project.clj`
  (see `use-buttle` [2] for a simple example).

		:dependencies [[buttle "1.0.0"] ,,,]

[1] https://clojars.org/repo/buttle/buttle/  
[2] https://github.com/henrik42/use-buttle  

## Extending _Buttle_ at runtime

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
  `<path-to>/buttle-driver.jar` and class `buttle.jdbc.Driver`.

__(3)__ In the GUI add an _Alias_ with URL (replace `<user>` and
  `<password>`). Text following `jdbc:buttle:` will be
  `read`/`eval`'ed as Clojure map form.

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
		<resource-root path="buttle-driver.jar"/>
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
  Postgres through the `DriverManager` (see section __A note on
  authentication__ below for details on how authentication works with
  _Buttle_).

    <datasource jndi-name="java:/jdbc/buttle-ds" pool-name="buttle-ds">
        <driver>buttle-driver</driver>
        <connection-url>
	        jdbc:buttle:{
		        :user "<user>"
		        :password "<password>"
		        :class-for-name "org.postgresql.Driver"
		        :target-url "jdbc:postgresql://<host>:<port>/<db-id>"}
	    </connection-url>
    </datasource>

Note: if you define a `<datasource>` with `<connection-url>` then
Wildfly will create a `javax.sql.DataSource` __datasource proxy__ that
uses the `java.sql.DriverManager` API to lookup the JDBC driver and
open connections. So in the example above it will effectively use
`buttle.jdbc.Driver` and not `buttle.jdbc.DataSource`. Below you find
an example which involves `buttle.jdbc.DataSource`.

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

Since _Buttle_ itself doesn't give you much functionality beyond
delegation logic you probably want to define `buttle.user-file` system
property to have _Buttle_ load your _hook code_:

    <system-properties>
      <property name="buttle.user-file" value="<path-to>/buttle-user-file.clj" boot-time="true"/>
    </system-properties>

#### Using `buttle.jdbc.DataSource`

Depending on how you define the `<datasource>` Wildfly will use the
`java.sql.DriverManager`/`java.sql.Driver` (see above) or
`javax.sql.DataSource` API. The following example shows how to make
Wildfly use the `javax.sql.DataSource` API:

    <datasource jndi-name="java:jboss/datasources/buttle-ds" pool-name="buttle-ds">
          <driver>buttle-driver</driver>
          <datasource-class>buttle.jdbc.DataSource</datasource-class>
          <security>
              <user-name>postgres-user</user-name>
              <password>postgres-password</password>
          </security>
          <connection-property name="DelegateSpec">
              {:delegate-class org.postgresql.ds.PGSimpleDataSource :url "jdbc:postgresql://127.0.0.1:6632/postgres"}
          </connection-property>
    </datasource>

Note that for `connection-property` there __must be no linebreak__ in
the element value!

#### A note on authentication

Usually JEE containers let you define a datasource and with it supply
authentication credentials (e.g. username and password). For Wildfly
this is done through the `security` element:

	    <xa-datasource jndi-name="java:/jdbc/postgres-xa" pool-name="postgres-xa">
	      <driver>postgres-driver</driver>
	      <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
	      <security>
	      	<user-name>postgres-user</user-name>
	      	<password>postgres-password</password>
	      </security>
	      <xa-datasource-property name="Url">jdbc:postgresql://127.0.0.1:6632/postgres</xa-datasource-property>
	    </xa-datasource>

For IBM WAS you enter __security aliases__ (see below).

Then when your application needs a JDBC connection it calls
`javax.sql.DataSource.getConnection()` on the datasource which it
usually retrieves from JNDI. Your app rarely calls
`javax.sql.DataSource.getConnection(String username, String password)`
since no-one wants to give authentication credentials to your
app. That's why it is given to the JEE container only.

When calling `getConnection()` your app will be talking to a
`javax.sql.DataSource` __datasource proxy__ that the JEE container
puts between your code and the _real_ datasource (which may even be an
XA-datasource really; like in the example shown above). If you have
given authentication credentials explicitly to the container (like
shown above) then the container's __datasource proxy__ will call
`javax.sql.DataSource.getConnection(String username, String password)`
on the _real_ datasource when delegating your call and thus supplying
the configured authentication credentials for connecting to the
database.

Instead of giving authentication credentials explicitly to the
container (like shown above) you can usually set some of the _real_
datasource's Java-Beans property values to give it username and
password.

Note that in this case the container has no explicit knowledge about
the details on how and what authentication credentials you give to the
datasource. And it depends on the JDBC datasource class which
Java-Beans properties you have to set (e.g. it's `User` and `Password`
for Postgres, but it may be `UserName` and `Passphrase` for some other
driver).

For Wildfly and Postgres this looks like this:

	    <xa-datasource jndi-name="java:/jdbc/postgres-xa" pool-name="postgres-xa">
	      <driver>postgres-driver</driver>
	      <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
	      <xa-datasource-property name="Url">jdbc:postgresql://127.0.0.1:6632/postgres</xa-datasource-property>
	      <xa-datasource-property name="User">postgres-user</xa-datasource-property>
	      <xa-datasource-property name="Password">postgres-password</xa-datasource-property>
	    </xa-datasource>

For IBM WAS you set the datasource's __custom properties__ (see
below).

Now when your app calls `javax.sql.DataSource.getConnection()` on the
__datasource proxy__ this time the proxy will call `getConnection()`
(instead of `getConnection(String, String)`) on the underlying _real_
datasource. In this case the _real_ datasource must, should and
usually will use the Java-Beans property values for `User` and
`Password` (or whatever property it uses) to authenticate against the
database. Note though that JDBC drives may support even more ways to
supply authentication credentials (like Postgres which can take these
from the JDBC URL).

When using a _Buttle_ datasource (to proxy the _real_ datasource) all
this is working just the same way. Only now the _Buttle_ datasource
(proxy) introduces an additional indirection/delegation step.

You configure authentication for the _Buttle_ datasource just like you
do it for the _real_ datasource.

With the following configuration the container will call
`getConnection(String, String)` on the _Buttle_ datasource which in
turn calls `getConnection(String, String)` on the _real_ datasource:

	    <xa-datasource jndi-name="java:/jdbc/buttle-xa" pool-name="buttle-xa">
	      <driver>buttle-driver</driver>
	      <xa-datasource-class>buttle.jdbc.XADataSource</xa-datasource-class>
	      <security>
			<user-name>postgres-user</user-name>
			<password>postgres-password</password>
	      </security>
	      <xa-datasource-property name="DelegateSpec">
			{
			 :delegate-class org.postgresql.xa.PGXADataSource
			 :url "jdbc:postgresql://127.0.0.1:6632/postgres"
			}
	      </xa-datasource-property>
	    </xa-datasource>

The _Buttle_ datasource classes support the map-typed Java-Bean
property `DelegateSpec`. Keys (other than `:delegate-class`) are used
to set the corresponding Java-Bean property of the _real_ datasource
(see below for more details). So in the following example we're
setting `Url`, `User` and `Password` Java-Beans property values.

        <xa-datasource jndi-name="java:/jdbc/buttle-xa" pool-name="buttle-xa">
          <driver>buttle-driver</driver>
          <xa-datasource-class>buttle.jdbc.XADataSource</xa-datasource-class>
          <xa-datasource-property name="DelegateSpec">
            {
              :delegate-class org.postgresql.xa.PGXADataSource
              :url "jdbc:postgresql://127.0.0.1:6632/postgres"
              :user "postgres-user"
              :password "postgres-password"
            }
          </xa-datasource-property>
        </xa-datasource>

In this case the container will call `getConnection()` on the _Buttle_
datasource which in turn calls `getConnection()` on the _real_
datasource which again must/will use the Java-Beans property values
that _Buttle_ has set before.

So when configuring authentication for your datasource keep in mind
that the whole story starts with a call from the JEE container and
this call will be determined by how you configure the datasource that
your app retrieves from JNDI. From there the rest follows as described
above.

#### XA-datasource

In Wildfly you define an `<xa-datasource>` like this (for Postgres):

    <xa-datasource jndi-name="java:/jdbc/postgres-xa" pool-name="postgres-xa">
	  <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
      <driver>postgres-driver</driver>
      <security>
        <user-name>postgres-user</user-name>
        <password>postgres-password</password>
      </security>
      <xa-datasource-property name="Url">jdbc:postgresql://127.0.0.1:6632/postgres</xa-datasource-property>
    </xa-datasource>

Note that `security` configuration is included here only for you to be
able to test this datasource through the Wildfly admin console GUI in
isolation. When proxying this datasource with _Buttle_ these
`security` settings will never be used (as explaied in __A note on
authentication__).

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

In the WAS admin console navigate to __Resources/JDBC/JDBC
providers__, select __scope__ (i.e. your Cell/Node/Server), hit
__New...__.

__Step 1: Create new JDBC provider__

* select __Database type__: `User-defined`
* enter __Implementation class name__:
  `buttle.jdbc.ConnectionPoolDataSource` or `buttle.jdbc.XADataSource`
  (see above)
* enter __Name__ (e.g. `Buttle CP-DS`) and __Description__
* hit __Next__

__Step 2: Enter database class path information__

* enter __Class path__: `<path-to-buttle-driver.jar>`
* hit __Next__

__Step 3: Summary__

* just hit __Finish__


__Define _Buttle_ datasource__

Now you need to define one or more datasources. In the WAS admin
console navigate to __Resources/JDBC/Data sources__, select __scope__
(i.e. your Cell/Node/Server), hit __New...__.

__Step 1: Enter basic data source information__

* enter __Data source name__ (e.g. `buttle_cp_ds`) and __JNDI name__
  (e.g. `jdbc/buttle_cp_ds`)
* hit __Next__

__Step 2: Select JDBC provider__

* select __Select an existing JDBC provider__: e.g. `Buttle CP-DS` (see above)
* hit __Next__

__Step 3: Enter database specific properties for the data source__

* __Data store helper class name__: do not change default
  `com.ibm.websphere.rsadapter.GenericDataStoreHelper`
* __Use this data source in container managed persistence (CMP)__: do
  not change default _checked_
* just hit __Next__

__Step 4: Setup security aliases__

* set all selections in __Select the authentication values for this
  resource__ to __(none)__. In this case authentication must come from
  _Buttle_ datasource configuration which is described below (see also
  section __A note on authentication__ above).
* hit __Next__

__Step 5: Summary__

* just hit __Finish__


__Configure _Buttle_ datasource__

Before you can use/test the _Buttle_ datasource you need to configure
it. Navigate to __Resources/JDBC/Data sources__, select __scope__
(i.e. your Cell/Node/Server) and click on the _Buttle_ datasource you
want to configure. Click on __Additional Properties__/__Custom
properties__.

If you have not added `delegateSpec` yet hit __New...__ to add a new
entry. Otherwise click on `delegateSpec` entry in the table.

When adding a new entry enter __Name__ `delegateSpec`.

Enter/change the entry __Value__.

__Example: Postgres__

	{:delegate-class org.postgresql.ds.PGConnectionPoolDataSource
	 :databaseName "<postgres-db-name>"
	 :password "<postgres-password>"
	 :portNumber (int <postgres-port-number>)
	 :serverName "<postgres-hostname>"
	 :user "<postgres-user>"}

Then hit __OK__.

__Test _Buttle_ datasource__

Finally we can test/use the _Buttle_ datasource. Navigate to
__Resources/JDBC/datasources__, select __scope__ (e.g. your
cell/node/server). Select (checkbox) the datasource. Hit __Test
connection__. If you have just changed the datasource you may get an
error stating that you have to _synchronize_ the datasource settings
first. Go ahead and click __Synchronize__ and then repeat the test.

### Clojure

Here _Buttle_ UBERJAR (includes Clojure) is used like you would use
any other JDBC driver.

	C:>java -cp buttle-0.1.0-SNAPSHOT-driver.jar;postgresql-9.4.1212.jar clojure.main -r
	Clojure 1.8.0
	user=> (use 'buttle.driver-manager)
	;;--> nil
	(-> (get-connection "jdbc:buttle:{:user \"<user>\" :password \"<password>\" :target-url \"jdbc:postgresql://127.0.0.1:6632/postgres?\"}" nil nil)
		.createStatement
		(.executeQuery "select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'")
		(resultset-seq))

In [1] you find an example on how to use _Buttle_ as a lib.

[1] https://github.com/henrik42/use-buttle

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

	C:\>java -cp buttle-driver.jar;postgresql-9.4.1212.jar;target -Dbuttle_user=<user> -Dbuttle_password=<password> ButtleTest

## Tests

You'll need a Postgres at `127.0.0.1:6632` (but see `jdbc-url` in
`test/buttle/core_test.clj`).

__Windows__

	> set buttle_user=<user>
	> set buttle_password=<password>
	> lein test
	
__Linux__

	$ buttle_user=<user> buttle_password=<password> lein test

## Building & Releasing

See also aliases in `project.clj`.

* build lib-jar: `lein jar`
* build UBERJAR (`buttle-driver.jar`): `lein uberjar`
* deploy lib-jar and UBERJAR to Nexus running on local machine: `lein with-profile +local deploy-all`
* release:  
		`lein with-profile +skip-test release-prepare!`  
		`lein with-profile +local release-deploy!`  
		`lein with-profile +local release-finish!`  

## TODOS

* review uses of butte.proxy/make-proxy and buttle.util/with-tccl --
  which classloader is picked? And why.

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
