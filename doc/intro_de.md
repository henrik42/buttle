# Einführung in Buttle

_Buttle_ ist ein JDBC Treiber [1, 2]. _Buttle_ selbst stellt jedoch
keine Verbindung zu einer Datenbank her. Stattdessen fungiert _Buttle_
als __Proxy__ (oder _Wrapper_) zu einem _echten_ JDBC Treiber.

_Buttle_ stellt die Klasse `buttle.jdbc.Driver`. Die Klasse
implementiert das Interface `java.sql.Driver` [3]. Damit kann _Buttle_
wie jeder andere JDBC Treiber in einer Anwendung verwendet werden.

_Buttle_ benötigt natürlich den _echten_ JDBC Treiber, an den er die
eingehenden Methodenaufrufe _deligieren_ kann, damit auch tatsächlich
ein Datenbankzugriff erfolgt. Es gibt verschiedene Möglichkeiten, wie
man die Beziehung von _Buttle_ als Proxy zu einem anderen JDBC Treiber
herstellt (vgl. unten).

Sobald _Buttle_ als Proxy eingerichtet ist, führt er zu folgenden
Effekten:

* `java.sql.Driver/connect(String url, Properties info)`: Die
  `java.sql.Connection` des _echten_ JDBC Treibers wird mit einem
  _Buttle_ Proxy versehen und der Proxy wird als Rückgabewert
  geliefert.

* Der _Buttle_ `java.sql.Connection` Proxy liefert für alle
  überladenen `java.sql.Connection/createStatement` Methoden einen
  `java.sql.Statement` Proxy zu dem _echten_ `Statement`.

* Der `java.sql.Statement` Proxy liefert für alle Methoden, die ein
  `java.sql.ResultSet` liefern, auch wiederum einen Proxy um das
  _echte_ `ResultSet`.

Die _Buttle_ Proxys haben folgende Funktionen:

* __Logging:__ 

* __Events:__ beim Eintritt und beim Austritt (auch im
  `Exception`-Fall) der Methoden werden _Events_ erzeugt. Diese werden
  in einen _Kanal_ (`core.async` Channel) geschrieben. Man kann sich
  von außen an diesen Kanal _binden_, so dass man die _Events_
  konsumieren kann. Damit kann man z.B. Monitoring betreiben.



[1] https://docs.oracle.com/javase/tutorial/jdbc/
[2] https://www.tutorialspoint.com/jdbc/
[3] https://docs.oracle.com/javase/8/docs/api/java/sql/Driver.html




# Buttle als Proxy zu einem anderen JDBC Treiber einrichten

wie stellt man die Beziehung zwischen Buttle und dem anderen Proxy her?



# Notizen

* Driver Interface: was liefern die einzelnen Methoden?


* Mehrere JDBC Verbindungen um verschiedene JDBC Verbindungen?

* Buttle als XATreiber?

* Buttle im App-Server?

* Buttle als DataSource?

