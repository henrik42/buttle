# Einführung in Buttle

_Buttle_ ist ein JDBC-Treiber [1, 2]. _Buttle_ selbst stellt jedoch
keine Verbindung zu einer Datenbank her. Stattdessen fungiert _Buttle_
als __Proxy__ (oder _Wrapper_) zu einem _echten_ JDBC-Treiber.

_Buttle_ stellt die Klasse `buttle.jdbc.Driver`. Die Klasse
implementiert das Interface `java.sql.Driver` [3]. Damit kann _Buttle_
wie jeder andere JDBC-Treiber in einer Anwendung verwendet werden.

_Buttle_ benötigt natürlich den _echten_ JDBC-Treiber, an den er die
eingehenden Methodenaufrufe _deligieren_ kann, damit auch tatsächlich
ein Datenbankzugriff erfolgt. Es gibt verschiedene Möglichkeiten, wie
man die Beziehung von _Buttle_ (als Proxy) zu einem _echten_
JDBC-Treiber herstellt (vgl. unten).

Sobald _Buttle_ als Proxy eingerichtet ist, führt er zu folgenden
Effekten:

* `java.sql.Driver/connect(String url, Properties info)`: Die
  `java.sql.Connection` des _echten_ JDBC-Treibers wird mit einem
  _Buttle_ `Connection`-Proxy versehen und der Proxy wird als
  Rückgabewert geliefert.

* Dieser _Buttle_ `Connection`-Proxy liefert für alle überladenen
  `java.sql.Connection/createStatement` Methoden einen _Buttle_
  `Statement`-Proxy zu dem `java.sql.Statement` des _echten_
  JDBC-Treibers.

* Der `Statement`-Proxy liefert für alle Methoden, die ein
  `java.sql.ResultSet` liefern, auch wiederum einen _Buttle_
  `ResultSet`-Proxy um das `ResultSet` des _echten_ JDBC-Treibers.

Die _Buttle_ Proxys haben folgende Funktionen:

* __Events:__ beim Eintritt und beim Austritt (auch im
  `Exception`-Fall) der Methoden werden _Events_ erzeugt. Diese werden
  in einen _Kanal_ (`core.async` Channel) geschrieben. Man kann sich
  von außen an diesen Kanal _binden_, so dass man die _Events_
  konsumieren kann. Damit kann man z.B. Monitoring und
  Performanzmessungen unterstützen.

* __Logging:__ es gibt einen in _Buttle_ eingebauten Konsumenten für
  _Events_, der die _Events_ in ein Log ausgibt.

[1] https://docs.oracle.com/javase/tutorial/jdbc/
[2] https://www.tutorialspoint.com/jdbc/
[3] https://docs.oracle.com/javase/8/docs/api/java/sql/Driver.html

# Buttle als Proxy zu einem echten JDBC-Treiber einrichten

## java.sql.DriverManager und java.sql.Driver

Die Klasse `java.sql.DriverManager` wird von einer Anwendung
verwendet, um eine Verbindung zu einer Datenbank aufzubauen
(`getConnection`).

Intern verwendet der `DriverManager` dazu eine Menge von
__registrierten__ `java.sql.Driver`. Jeder JDBC-Treiber (z.B. für
Postgres, DB2, Oracle) liefert eine Klasse, die dieses Interface
implementiert. _Buttle_ liefert ebenfalls eine solche Klasse
(`buttle.jdbc.Driver`).

Es gibt zwei Methoden, durch die die Registrierung des/der `Driver`
erfolgt:

* __gesteuert durch die Anwendung__: die Anwendung lädt aktiv/explizit
  die Klasse des gewünschten JDBC-Treibers. I.d.R. erfolgt dies via
  `Class.forName`. Es ist für die Anwendung nicht nötig (aber
  möglich/zulässig), eine Instanz dieser Klasse zu erzeugen und diese
  Instanz anschließend zu verwenden.

		Class.forName("buttle.jdbc.Driver")

* __gesteuert durch den `DriverManager`__:

Unabhängig davon, über welchen dieser beiden Mechanismen der
JDBC-Treiber geladen wird, ist es Aufgabe der `Driver`-Klasse, sich
selber aktiv beim `DriverManager` zu registrieren. Das machen die
`Driver`-Klassen durch einen `static` Initializer.




# Beispiele




# Notizen

* Hinweis: es ist auch möglich, direkt den Driver anstatt des
  DriverManager zu verwenden. Aber dann hat man den Proxy nur wenn man
  eben Buttle direkt verwendet. Buttle kann sich dann nicht
  "dazwischen" hängen.

* Buttle erweitern

* Buttle hacken

* Driver Interface: was liefern die einzelnen Methoden?

* Mehrere JDBC Verbindungen um verschiedene JDBC Verbindungen?

* Buttle als XATreiber?

* Buttle im App-Server?

* Buttle als DataSource?

* Da Buttle Proxy-basiertes AOP ist, fehlen bestimmte Dinge, die man
  mit CGlib erreichen kann. Genau wie in Spring.
