# What is it?

_Buttle_ is a proxying JDBC driver which wraps JDBC drivers.

_Buttle_ supplies a `java.sql.Driver` which delegates calls to a
backing `Driver` (like `org.postgresql.Driver`). _Buttle_ then
constructs Proxys _around_ the returned values. These Proxys then do
the same for their proxied Instances (and so on).

Proxys are only constructed for methods (i.e. their returned values)
that have interface typed declared return types.

__(Not implemented yet!)__ _Buttle_ proxys create _events_ for every
method invocation and completion incl. when an `Exception` is
thrown. These events include info about the invoked class/method, the
timestamp of the event, arguments and returned value / `Exception` and
duration.

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

__Clojure__

	__TBD__

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

