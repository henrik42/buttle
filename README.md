# What is it?

_Buttle_ is a proxying JDBC driver which wraps JDBC drivers.

_Buttle_ supplies a `java.sql.Driver` which delegates calls to a
backing `Driver` (like `org.postgresql.Driver`). _Buttle_ then
constructs Proxys _around_ the returned values. These Proxys then do
the same for their proxied Instances (and so on).

Proxys are only constructed for methods (i.e. their returned values)
that have interface typed declared return types.

__Not implemented yet!__

_Buttle_ proxys create _events_ for every method invocation and
completion incl. when an `Exception` is thrown. These events include
info about

* timestamp of the event
* duration (for completion/`Exception`)
* invocation stacktrace
* invoked class/method
* arguments
* returned value/`Exception`

Events are communicated through a `core.async` channel so that users
can consume that channel to receive the events.

# What to use it for?

Use it for

* troubleshooting

* debugging

* performance measurements

* application monitoring

# How to extend?

__TBD__

# Examples

__SQuirreL__

__(1)__ Add UBERJAR to SQuirrelL's classpath in `squirrel-sql.bat`. 

	set TMP_CP="%SQUIRREL_SQL_HOME%\squirrel-sql.jar;<path-to>\buttle-0.1.0-SNAPSHOT-standalone.jar"

__Note:__ Adding the _Buttle_ UBERJAR via _Driver_ / _Extra Class Path_
  did not work for me due to classloading handling in
  Clojure. So I really had to add it to the JVMs classpath as shown
  here.

__(2)__ Add a _Driver_ with class `buttle.jdbc.Driver`. 

__(3)__ Add _Alias_ with URL (replace `<user>` and `<password>`). Text
  following `jdbc:buttle:` will be read as Clojure map form.

	jdbc:buttle:{:user "<user>" :password "<password>" :target-url "jdbc:postgresql://127.0.0.1:6632/postgres"}

__Clojure__

__Note:__ Should work without `register-driver` via
`META-INF/services/java.sql.Driver` -- must be fixed.

	C:>java -cp buttle-0.1.0-SNAPSHOT-standalone.jar;postgresql-9.4-1206-jdbc41.jar clojure.main -r
	Clojure 1.8.0
	user=> (use 'buttle.driver-manager)
	;;--> nil
	user=> (register-driver (buttle.jdbc.Driver.))
	;;--> nil
	(-> (get-connection "jdbc:buttle:{:user \"<user>\" :password \"<password>\" :target-url \"jdbc:postgresql://127.0.0.1:6632/postgres?\"}" nil nil)
		.createStatement
		(.executeQuery "select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'")
		(resultset-seq))

__Java__

	__TBD__

# Tests

You'll need a Postgres at `127.0.0.1:6632` (but see `jdbc-url` in
`test/buttle/core_test.clj`).

__Windows__

	> set buttle_user=<user>
	> set buttle_password=<password>
	> lein test
	
__Linux__

	$ buttle_user=<user> buttle_password=<password> lein test

