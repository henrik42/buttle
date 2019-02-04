# Buttle README

## What is it?

_Buttle_ is a proxying JDBC driver with hooks.

_Buttle_ supplies a `java.sql.Driver` (see `driver.clj`) which
delegates `connect` calls to a backing `Driver` (like
`org.postgresql.Driver`). _Buttle_ then constructs proxies _around_
the returned `Connection`. These proxies (see `proxy.clj`) then
recursivly do the same for their proxied instances (e.g. for
`Statement` and `ResultSet` return values).

Proxies are only constructed for methods (i.e. their returned values)
that have interface-typed declared return types.

_Buttle_ proxies delegate calls to the backing JDBC driver's instances
through `buttle.proxy/handle` multi method. Using
`buttle.proxy/def-handle` users can _inject_ their own method
implemenations (per interface/method) into the delegation. See usage
example in `examples/buttle/examples/open_tracing.clj` and
`examples/buttle/examples/handle.clj`.

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

Similar things have been done before:

* https://www.javaspecialists.eu/archive/Issue136.html  
* https://jaxenter.de/jdbc-treiber-selbstgebaut-java-trickkiste-636  
* http://jamonapi.sourceforge.net/jamon_sql.html  
* https://github.com/arthurblake/log4jdbc  
* https://p6spy.readthedocs.io/en/latest/index.html  

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

## Examples

### SQuirreL

__(1)__ In `squirrel-sql.bat` add system property `buttle.user-file` to java call:

	set BUTTLE="-Dbuttle.user-file=<path-to>/buttle/examples/handle.clj"
	java [...] %BUTTLE% [...]

__(2)__ Add a _Driver_ with _extra classpath_
  `<path-to>/buttle-standalone.jar` and class `buttle.jdbc.Driver`.

__(3)__ Add _Alias_ with URL (replace `<user>` and `<password>`). Text
  following `jdbc:buttle:` will be read as Clojure map form.

	jdbc:buttle:{:user "<user>" :password "<password>" :target-url "jdbc:postgresql://127.0.0.1:6632/postgres"}

### Wildfly

When using _Buttle_ in Wildfly (either _domain_ mode oder
_standalone_) you can either include it in your application (but
usually JDBC drivers are not included in the main app) or you can
deploy it as a _module_ (tested with Wildfly 12.0.0.Final).

For this you have to:

* define a `module`
* define a `driver`
* define a `datasource`

__(1)__ Define `module`: put this into
  `<wildfly-root>/modules/buttle/main/module.xml`. You may have to
  adjust `path` to _Buttle_'s JAR filename. Note that you have to
  include `dependencies` for the `javax.api` base module and the JDBC
  driver you want to wrap. Otherwise _Buttle_ won't be able to _see_
  the JDBC driver's classes.

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

__(2)__ Define `driver`: here we also define the Postgres driver we
  want to wrap with _Buttle_. Note that Wildfly does not need to know
  _Buttle_'s driver class (`buttle.jdbc.Driver`). It relies on
  _Buttle_ being loaded via SPI
  (`META-INF/services/java.sql.Driver`). Wildfly then finds the
  _Buttle_ driver via `DriverManager/getConnection`.

    <drivers>
      <driver name="buttle-driver" module="buttle"/>
      <driver name="postgresql" module="postgres"/>
    </drivers>

__(3)__ Define `datasource`: 

    <datasource jndi-name="java:/jdbc/buttle-ds" pool-name="buttle-pool" use-java-context="true">
        <driver>buttle-driver</driver>
        <connection-url>jdbc:buttle:{:user "<user>" :password "<password>"
		                             :target-url "jdbc:postgresql://<host>:<port>/<db-name>"}
		</connection-url>
    </datasource>

Since _Buttle_ itself doesn't give you much functionality you probably
want to define `buttle.user-file` system property to have _Buttle_
load your _hook code_:

    <system-properties>
      <property name="buttle.user-file" value="<path-to>/buttle-user-file.clj" boot-time="true"/>
    </system-properties>

### Websphere

__TBD__

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

Use `lein make-uberjar` to build the minimum _Buttle_ UBERJAR. It'll
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

* add `:class-for-name` option to _Buttle_ JDBC-URL so that _Buttle_
  can be used to load JDBC drives when the SPI doesn't work.

* add `:driver-class` option to _Buttle_ JDBC-URL so that _Buttle_
  directly instanciates and uses that driver to delegate to (and not
  indirectly delegate to the driver by calling DriverManager/connect).

* add `:datasource-jndi-name` option to _Buttle_ JDBC-URL so that
  _Buttle_ fetches a `DataSource` (not a `Driver`) from JNDI and
  delegates to that.
