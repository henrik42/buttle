# Buttle README

## What is it?

_Buttle_ is a proxying JDBC driver which wraps _real_ JDBC drivers.

_Buttle_ supplies a `java.sql.Driver` which delegates calls to a
backing `Driver` (like `org.postgresql.Driver`). _Buttle_ then
constructs proxies _around_ the returned values. These proxies then do
the same for their proxied instances (and so on).

Proxies are only constructed for methods (i.e. their returned values)
that have interface-typed declared return types.

_Buttle_ proxies create _events_ for every method invocation and
completion incl. when an `Exception` is thrown. These events include
info about

* timestamp of the event
* duration (for completion/`Exception`)
* stacktrace (for exceptions)
* invoked class/method
* arguments
* returned value/`Exception`

Events are communicated through a `core.async` channel so that users
can consume that channel to receive the events.

## What to use it for?

Use it for

* troubleshooting

* debugging

* performance measurements

* application monitoring

## How to extend?

There are two ways to _hook into_:

__events__: receive events from _Buttle_ through
  `buttle.event/event-mult` like this:

	(let [ch (clojure.core.async/chan)]
	  (clojure.core.async/tap buttle.event/event-mult ch)
	  (clojure.core.async/go
	   (loop []
		 (when-let [e (clojure.core.async/<! ch)]
		   (println e) ;; do something with the event
		   (recur)))))

__multi method__: _install_ your own proxy for _target_
  interfaces/methods. This acts like an AOP advice/proxy. Note that in
  this case you have to take care to send events if you need that (see
  `buttle.proxy/handle-default`).

You can (re-) register the `buttle.proxy/handle :default`. 

	(defmethod buttle.proxy/handle :default [the-method target-obj the-args]
	  (do-some-thing-with-call the-method target-obj the-args))

And you can register multi method implementation for just specific
interfaces, methods or a combination (see `test/buttle/proxy_test.clj`
for more examples):

    (buttle.proxy/def-handle [java.sql.Connection :buttle/getCatalog] [the-method target-obj the-args]
      (str "Connection/getCatalog: intercepted " (.getName the-method)))

## Examples

__SQuirreL__

__(1)__ Add UBERJAR to SQuirrelL's classpath in `squirrel-sql.bat`. 

	set TMP_CP="%SQUIRREL_SQL_HOME%\squirrel-sql.jar;<path-to>\buttle-0.1.0-SNAPSHOT-standalone.jar"

__Note:__ Adding the _Buttle_ UBERJAR via _Driver_ / _Extra Class Path_
  did not work for me due to classloading handling in
  Clojure. So I really had to add it to the JVMs classpath as shown
  here.

If you want to see events in the squirrel log:

	set BUTTLE_USER_FORM="-Dbuttle.user-form=(load-file ""C:/buttle-workspace/examples/buttle/examples/event_channel.clj"")"
	java %BUTTLE_USER_FORM% [...]

__(2)__ Add a _Driver_ with class `buttle.jdbc.Driver`. 

__(3)__ Add _Alias_ with URL (replace `<user>` and `<password>`). Text
  following `jdbc:buttle:` will be read as Clojure map form.

	jdbc:buttle:{:user "<user>" :password "<password>" :target-url "jdbc:postgresql://127.0.0.1:6632/postgres"}

__Clojure__

	C:>java -cp buttle-0.1.0-SNAPSHOT-standalone.jar;postgresql-9.4.1212.jar clojure.main -r
	Clojure 1.8.0
	user=> (use 'buttle.driver-manager)
	;;--> nil
	(-> (get-connection "jdbc:buttle:{:user \"<user>\" :password \"<password>\" :target-url \"jdbc:postgresql://127.0.0.1:6632/postgres?\"}" nil nil)
		.createStatement
		(.executeQuery "select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'")
		(resultset-seq))

__Java__

	import java.sql.Connection;
	import java.sql.DriverManager;
	import java.sql.ResultSet;
	import java.sql.Statement;

	public class ButtleTest {

		public static void processEvent(Object e) {
			System.out.println("event : " + e);
		}

		public static void main(String[] args) throws Exception {

			System.setProperty("buttle.user-form", "(load-file \"examples/buttle/examples/java_events.clj\")");

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

	C:\>java -cp buttle-0.1.0-SNAPSHOT-standalone.jar;postgresql-9.4.1212.jar;target -Dbuttle_user=<user> -Dbuttle_password=<password> ButtleTest

## Tests

You'll need a Postgres at `127.0.0.1:6632` (but see `jdbc-url` in
`test/buttle/core_test.clj`).

__Windows__

	> set buttle_user=<user>
	> set buttle_password=<password>
	> lein test
	
__Linux__

	$ buttle_user=<user> buttle_password=<password> lein test

