# Einführung in Buttle

_Buttle_ ist ein JDBC-Treiber [1, 2]. _Buttle_ selbst stellt jedoch
keine Verbindung zu einer Datenbank her. Stattdessen fungiert _Buttle_
als __Proxy__ (oder _Wrapper_) zu einem _echten_ JDBC-Treiber.

_Buttle_ stellt die Klasse `buttle.jdbc.Driver`. Die Klasse
implementiert das Interface `java.sql.Driver` [3]. Damit kann _Buttle_
wie jeder andere JDBC-Treiber in einer Anwendung verwendet werden.

_Buttle_ benötigt natürlich den _echten_ JDBC-Treiber, an den er die
eingehenden Methodenaufrufe _deligieren_ kann, damit auch tatsächlich
ein Datenbankzugriff erfolgt (_Buttle_ ist __kein__ _Mock_; könnte man
aber auch mal drüber nachdenken...). Es gibt verschiedene
Möglichkeiten, wie man die Beziehung von _Buttle_ (als Proxy) zu einem
_echten_ JDBC-Treiber herstellt (vgl. unten).

Sobald _Buttle_ als Proxy eingerichtet ist, führt er zu folgenden
Effekten:

* Die `java.sql.Connection` des _echten_ JDBC-Treibers (die man durch
  `java.sql.Driver/connect` erhält) wird mit einem _Buttle_
  `Connection`-Proxy versehen und der Proxy wird als Rückgabewert
  geliefert.

* Dieser _Buttle_ `Connection`-Proxy liefert für alle überladenen
  `java.sql.Connection/createStatement` Methoden einen _Buttle_
  `Statement`-Proxy zu dem `java.sql.Statement` des _echten_
  JDBC-Treibers.

* Der `Statement`-Proxy liefert für alle Methoden, die ein
  `java.sql.ResultSet` liefern, auch wiederum einen _Buttle_
  `ResultSet`-Proxy um das `ResultSet` des _echten_ JDBC-Treibers.

Die verschiedenen _Buttle_ Proxys haben folgende Funktionen:

* __Events:__ beim Eintritt und beim Austritt (auch im
  `Exception`-Fall) der Methoden werden _Events_ erzeugt. Diese werden
  in einen _Kanal_ (`core.async` Channel) geschrieben. Man kann sich
  von außen an diesen Kanal _binden_ (wie ein _Observer_), so dass man
  die _Events_ konsumieren kann. Damit kann man z.B. __Monitoring__
  und __Performanzmessungen__ unterstützen.

* __Logging:__ es gibt einen in _Buttle_ eingebauten Konsumenten für
  _Events_, der die _Events_ in ein Log ausgibt.

[1] https://docs.oracle.com/javase/tutorial/jdbc/  
[2] https://www.tutorialspoint.com/jdbc/  
[3] https://docs.oracle.com/javase/8/docs/api/java/sql/Driver.html  

# Buttle als Proxy zu einem echten JDBC-Treiber einrichten

## java.sql.DriverManager und java.sql.Driver

Die Methode `java.sql.DriverManager/getConnection` wird von
Anwendungen verwendet, um eine Verbindung zu einer Datenbank
aufzubauen.

Intern verwendet der `DriverManager` dazu eine Menge von
__registrierten__ `java.sql.Driver` (vgl. unten). Jeder JDBC-Treiber
(z.B. für Postgres, DB2, Oracle) liefert eine Klasse, die dieses
`Interface` implementiert. _Buttle_ liefert ebenfalls eine solche
Klasse (`buttle.jdbc.Driver`).

__Hinweis__: es ist __nicht notwendig__, dass diese Klasse einen
publiken Konstruktor besitzt. Man kann also im allgemeinen __nicht__
davon ausgehen, dass man selber diese Klasse instanziieren kann.

Es gibt drei Methoden, durch die die Registrierung des/der `Driver`
erfolgt:

* __gesteuert durch die Anwendung__: die Anwendung lädt aktiv/explizit
  eine Klasse des gewünschten JDBC-Treibers. I.d.R. erfolgt dies via
  `Class.forName`. D.h. in diesem Fall muss die Anwendung wissen, wie
  der JDBC-Treiber geladen wird und sie muss die entsprechende
  Ladelogik liefern.  
  __Hinweis:__ es muss sich bei dieser Klasse __nicht zwingend__ um
  jene Klasse handeln, die das Interface `java.sql.Driver`
  implementiert (häufig ist das aber der Fall). Die einzige
  Anforderung ist, dass die geladene Klasse
  den/die `Driver` am `DriverManager` registriert (vgl. unten).  
  __Beispiel:__

		Class.forName("org.postgresql.Driver")

* __gesteuert durch den__ `DriverManager`: sobald die Klasse
  `java.sql.DriverManager` geladen wird (z.B. weil die Anwendung eine
  Methode des `DriverManager` aufruft), wird während der statischen
  Initialisierung via `java.util.ServiceLoader.load(Driver.class)`
  versucht, _Service-Provider_ für `java.sql.Driver` zu finden. Das
  bedeutet, dass nach der Resource `META-INF/services/java.sql.Driver`
  gesucht wird. Falls sie gefunden wird (und es kann mehrere Vorkommen
  geben!), wird für jedes Vorkommen der Inhalt der Resource als
  Klassennamen interpretiert und die genannte Klasse wird geladen.  
  __Hinweis:__ Im Falls des _Service-Providers_ __muss__ die
  angegebene Klasse einen argumentlosen Konstruktor haben!

* __gesteuert durch den__ `DriverManager`: ebenfalls während der
  statischen Initialisierung, wertet der `DriverManager` die
  System-Property `jdbc.drivers` aus. Der Wert muss eine `:`-getrennte
  Liste von Klassennamen sein. Der `DriverManager` iteriert über diese
  Liste und ruft für jeden Klassennamen `<className>` die Methode
  `Class.forName(<className>)` auf.  
  __Beispiel:__

		-Djdbc.drivers='org.postgresql.Driver:oracle.jdbc.driver.OracleDriver'

Unabhängig davon, über welchen dieser Mechanismen der JDBC-Treiber
geladen wird, ist es Aufgabe der geladenen Klasse, den/die `Driver`
aktiv via `java.sql.DriverManager/registerDriver` beim `DriverManager`
zu registrieren. D.h. das __Laden der Klassen__ kann durch den
`DriverManager` erfolgen, er übernimmt aber __nicht__ selber das
__Registrieren__ der `Driver`.

__Wichtig:__ weder die Anwendung noch der `DriverManager` erzeugen
selber Instanzen der `Driver` Klasse(n) des JDBC-Treibers. Auch
_Buttle_ kann nicht von sich aus Instanzen diese Klassen
erzeugen. Instanzen werden nur intern im JDBC-Treiber erzeugt und dann
an den `DriverManager` übergeben.

## buttle.jdbc.Driver

Der _Buttle_ `Driver` kann wie jeder andere JDBC-Treiber geladen
werden (vgl. oben). Es werden alle drei oben erläuterten Mechanismen
unterstützt (d.h. der Treiber liefert auch eine passende
`META-INF/services/java.sql.Driver`). Falls der `Driver` über die
Anwendungslogik geladen werden soll und/oder über die System-Property
`jdbc.drivers`, muss als zu ladene Klasse `buttle.jdbc.Driver`
verwendet werden.

_Buttle_ hat keine direkte/explizite Kenntnis davon, für welche(n)
_echten_ JDBC-Treiber er als Proxy eingesetzt werden soll.

Es gibt die Möglichkeit, dass der _echte_ Treiber über einen der drei
obigen Mechanismen geladen wird. _Buttle_ muss dann (a) dafür sorgen,
dass der eigene `Driver` (anstatt des _echten_) für die JDBC-Zugriffe
verwendet wird und _Buttle_ muss (b) Zugriff auf den _echten_ `Driver`
bekommen, um durch die eigenen Proxys an ihn zu deligieren.

Dabei muss unterschieden werden, ob die Anwendung eine JDBC-URL
verwendet, die (c) vom _echten_ JDBC-Treiber unterstützt wird
(z.B. `jdbc:postgres:` für Postgres), oder ob (d) die Anwendung eine
_Buttle_-URL verwendet (z.B. `jdbc:buttle:`).

Im (einfacheren) Fall (d) sorgt der `DriverManager` selber für (a), da
kein anderer `Driver` die _Buttle_-URL unterstützen wird.

Im (kniffligeren) Fall (c) ist die __Reihenfolge__ der (intern im
`DriverManager`) __registrierten__ `Driver` entscheidend: sollte
__erst__ der _Buttle_ `Driver` vom `DriverManager` angesprochen
werden, kann er auch zu einer `jdbc:postgres:` eine (Proxy)
`Connection` liefern. Wird jedoch erst der _echte_ `Driver`
angesprochen, so kann der _Buttle_ `Driver` nicht eingreifen.

_Buttle_ liefert eine Klasse, die den _Buttle_ `Driver` registriert
und alle __registrierten__ `Driver`, die 






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
