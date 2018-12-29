# (German) Einführung in Buttle

__Hinweis:__ Die folgende Doku beschreibt den Zielzustand für
_Buttle_. Ich habe erst einen Teil der beschriebenen Funktionalität
umgesetzt.

_Buttle_ ist ein JDBC-Treiber [1, 2]. _Buttle_ selbst stellt jedoch
keine Verbindung zu einer Datenbank her. Stattdessen fungiert _Buttle_
als __Proxy__ (oder _Wrapper_) zu einem _echten_ JDBC-Treiber.

_Buttle_ stellt die Klasse `buttle.jdbc.Driver`. Die Klasse
implementiert das Interface `java.sql.Driver` [3] und kann als
`Driver` am `java.sql.DriverManager` _registriert_ werden. Damit kann
_Buttle_ wie jeder andere JDBC-Treiber in einer Anwendung verwendet
werden.

_Buttle_ benötigt natürlich einen _echten_ JDBC-Treiber, an den er die
eingehenden Methodenaufrufe _deligieren_ kann, damit auch tatsächlich
ein Datenbankzugriff erfolgt (_Buttle_ ist __kein__ _Mock_; könnte man
aber auch mal drüber nachdenken...). Es gibt verschiedene
Möglichkeiten, wie man die Beziehung von _Buttle_ (als Proxy) zu einem
_echten_ JDBC-Treiber herstellt (vgl. unten).

Sobald _Buttle_ als Proxy eingerichtet ist, führt er zu folgenden
Effekten:

* Die `java.sql.Connection` des _echten_ JDBC-Treibers (die man durch
  `java.sql.Driver/connect` oder
  `java.sql.DriverManager/getConnection` erhält) wird mit einem
  _Buttle_ `Connection`-Proxy versehen und der Proxy wird als
  Rückgabewert geliefert.

* Dieser _Buttle_ `Connection`-Proxy liefert für alle überladenen
  `java.sql.Connection/createStatement` Methoden einen _Buttle_
  `Statement`-Proxy zu dem `java.sql.Statement` des _echten_
  JDBC-Treibers.

* Der `Statement`-Proxy liefert für alle Methoden, die ein
  `java.sql.ResultSet` liefern, auch wiederum einen _Buttle_
  `ResultSet`-Proxy um das `ResultSet` des _echten_ JDBC-Treibers.

Die verschiedenen _Buttle_ Proxys haben (neben der Delegation an das
gewrappte Objekt) folgende Funktionen:

* __Events:__ beim Eintritt und beim Austritt (auch im
  `Exception`-Fall) der Methoden werden _Events_ erzeugt. Diese werden
  in einen _Kanal_ (`core.async` channel) geschrieben. Man kann sich
  von außen an diesen Kanal _binden_ (wie ein _Observer_), so dass man
  die _Events_ konsumieren kann. Damit kann man z.B. __Monitoring__
  und __Performanzmessungen__ unterstützen.

* __Logging:__ es gibt einen in _Buttle_ schon eingebauten Konsumenten
  für _Events_, der die _Events_ in ein Log ausgibt.

Bisher unterstützen die Proxys keinen weiteren
Delegationsmechanismus. Es ist also nicht möglich, den Proxys einen
_Hander_ o.ä. zu übergeben, an den sie die Ausführung der Proxy-Logik
delegieren würden. Mit so einem Feature wäre es z.B. möglich, in
solchen Handlern __Mock-Funktionalitäten__ zu realisieren.

[1] https://docs.oracle.com/javase/tutorial/jdbc/  
[2] https://www.tutorialspoint.com/jdbc/  
[3] https://docs.oracle.com/javase/8/docs/api/java/sql/Driver.html  

# Buttle als Proxy zu einem echten JDBC-Treiber einrichten

Da _Buttle_ nur ein Proxy um einen _echten_ JDBC-Treiber ist, kann er
sich an bestimmte Stellen __nicht__ in die Beziehungen zwischen den
verschiedenen Objekten einklinken, die der _echte_ JDBC-Treiber
liefert/kontrolliert.

So gibt es z.B. eine Beziehung zwischen einem `java.sql.Statement` und
den `java.sql.ResultSet` Ojekten, die durch das `Statement` erzeugt
wurden (z.B. weil ein JDBC-Treiber _seine_ `ResutSet`s schließen muss,
wenn das `Statement` geschlossen wird, das sie erzeugt hat. Und dazu
muss das `Statement` wissen, welche offenen `ResultSet`s es in dem
Augenblick gibt). Dabei wird keiner der _Buttle_ Proxys
einbezogen. Die _Buttle_ Proxys befinden sich also nur _zwischen_ dem
__Anwendungscode__ und dem _echte_ JDBC-Treiber. Somit können von
_Buttle_ auch nur Aufrufe _abgefangen_ werden, die von der Anwendung
in die JDBC-API gehen.

Soweit ich weiß, unterstützt die JDBC-API kein _Factory-Pattern_, mit
dem es möglich wäre, direkt in die Erzeugung der verschiedenen
Instanzen (`Connection`, `Statement`, `ResultSet`) einzugreifen. Damit
wäre es dann möglich, die Proxys auch _zwischen_ den verschiedenen
Instanzen zu _installieren_ (so wie es z.B. Spring macht).

## `java.sql.DriverManager` und `java.sql.Driver`

I.d.R. wird von Anwendungen die Methode
`java.sql.DriverManager/getConnection` verwendet, um eine Verbindung
zu einer Datenbank aufzubauen.

Intern verwendet der `DriverManager` dazu eine Menge von
__registrierten__ `java.sql.Driver` (vgl. unten). Jeder JDBC-Treiber
(z.B. für Postgres, DB2, Oracle) liefert eine Klasse, die dieses
`Interface` implementiert. _Buttle_ liefert ebenfalls eine solche
Klasse (`buttle.jdbc.Driver`).

__Hinweis__: es ist __nicht notwendig__ (d.h. die JDBC-API
Spezifikation __fordert es nicht__), dass diese `Driver`-Klassen der
JDBC-Treiber einen publiken/argumentlosen Konstruktor besitzen. Man
kann also im allgemeinen __nicht__ davon ausgehen, dass man selber
diese Klassen instanziieren kann.

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
  den/die `Driver` am `DriverManager` __registriert__ (vgl. unten).  
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
  angegebene Klasse einen argumentlosen Konstruktor haben, da der
  _Service-Loader_ eine Instanz der Klasse erzeugt. Durch das
  Laden/Instanziieren muss dann wiederum die Registrierung des
  `Driver` am `DriverManager` erfolgen.

* __gesteuert durch den__ `DriverManager`: ebenfalls während der
  statischen Initialisierung, wertet der `DriverManager` die
  System-Property `jdbc.drivers` aus. Der Wert muss (wenn er denn
  überhaupt gesetzt ist) eine `:`-getrennte Liste von Klassennamen
  sein. Der `DriverManager` iteriert über diese Liste und ruft für
  jeden Klassennamen `<class-name>` die Methode
  `Class.forName(<class-name>)` auf.  
  __Beispiel:__

		-Djdbc.drivers='org.postgresql.Driver:oracle.jdbc.driver.OracleDriver'

Unabhängig davon, über welchen dieser Mechanismen der JDBC-Treiber
geladen wird, ist es Aufgabe der geladenen Klasse, den/die `Driver`
aktiv via `java.sql.DriverManager/registerDriver` beim `DriverManager`
zu __registrieren__. D.h. das __Laden der Klassen__ kann durch den
`DriverManager` erfolgen, er übernimmt aber __nicht__ selber
aktiv/explizit das __Registrieren__ des/der `Driver` (sondern er stößt
es eben nur indirekt an).

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
obigen Mechanismen geladen wird. _Buttle_ muss dann __(a)__ dafür
sorgen, dass der eigene `Driver` (anstatt des _echten_) für die
JDBC-Zugriffe durch die Anwendung verwendet wird und _Buttle_ muss
__(b)__ Zugriff auf den _echten_ `Driver` bekommen, um durch die
eigenen Proxys an ihn zu deligieren.

Als Ergänzung zu den drei oben beschriebenen Ladeoptionen kann der
_echte_ JDBC-Treiber auch über/durch _Buttle_ geladen werden
(vgl. unten). Dies entspricht dem ersten oben genannten Mechanismus,
nur dass in diesem Fall die _Ladelogik_ in _Buttle_ implementiert ist
und nicht in der Anwendung. _Buttle_ verwendet in diesem Fall
ebenfalls `Class.forName`. Ansonsten unterscheidet sich diese Option
nicht von den anderen.

Unabhängig davon, wie das Laden des _echten_ JDBC-Treibers
_angestoßen_ wird, muss unterschieden werden, ob die Anwendung eine
JDBC-URL verwendet, die __(A)__ vom _echten_ JDBC-Treiber unterstützt
wird (z.B. `jdbc:postgres:` für Postgres), oder ob __(B)__ die
Anwendung eine _Buttle_-URL verwendet (z.B. `jdbc:buttle:`).

## buttle.jdbc.DriverReplacingDriver

Im (einfacheren) Fall __(B)__ sorgt der `DriverManager` selber für
__(a)__, da kein anderer `Driver` die _Buttle_-URL unterstützen
wird. Für diesen Fall muss aber eben die JDBC-URL __in der Anwendung__
angepasst werden (das kann oder kann eben nicht gewünscht/möglich
sein).

Im (kniffligeren) Fall __(A)__ ist die __Reihenfolge__ der (intern im
`DriverManager`) __registrierten__ `Driver` entscheidend: sollte
__erst__ der _Buttle_ `Driver` vom `DriverManager` angesprochen werden
(vgl. `java.sql.DriverManager.getConnection`), kann er auch zu einer
_fremden/echten_ JDBC-URL (wie `jdbc:postgres:`) eine (Proxy)
`Connection` liefern. Dadurch wird er vom `DriverManager` für die
betreffende JDBC-URL _ausgewählt_ und kann anschließend einen
`Connection` Proxy liefen. Wird jedoch erst der _echte_ `Driver` (in
diesem Fall z.B. der Postgres-Treiber) angesprochen, so kann der
_Buttle_ `Driver` nicht eingreifen.

Daher liefert _Buttle_ die Klasse `buttle.jdbc.DriverReplacingDriver`,
die den _Buttle_ `Driver` registriert und alle (bis zu diesem
__Zeitpunkt__) __registrierten__ `Driver`, die __vor__ dem _Buttle_
`Driver` im `DriverManager` registriert sind, __deregistriert__ und
dann sofort wieder __registriert__. Dadurch werden diese
_erneut-registrierten_ `Driver` anschließend im `DriverManager` von
der Reihenfolge her _hinter_ dem _Buttle_ Driver geführt. D.h. es wird
die gewünschte Reihenfolge der `Driver` hergestellt, so dass _Buttle_
wie gewünscht eingreifen kann.

__Hinweis:__ man könnte das nun folgend beschrieben Verfahren auch
ohne __erneute__ Registrierung der `Driver` am `DriverManager`
umsetzen. Es wäre möglich, die `Driver`-_Auswahllogik_
(vgl. `java.sql.DriverManager.getConnection`) komplett im
`buttle.jdbc.Driver` zu implementieren.

Dieses Vorgehen/Verfahren des __erneuten Registrierens__ hat einige
Schwächen:

Beim __Deregistrieren__ teilt der `DriverManager` jeder `Driver`
Instanz (abhängig davon, wie der jeweilige `Driver` registriert wurde)
mit, dass sie gerade deregistriert wird. Dadurch kann es zu einer
__Zustandsänderung__ im `Driver` kommen. Zum Beispiel könnte es sein,
dass der `Driver` beim Deregistrieren bestimmte Ressourcen freigibt.

_Buttle_ muss die `Driver` anschließend ja wieder registrieren
(__re-registrieren__). Das kann _Buttle_ nicht auf die gleiche Art und
Weise machen, wie die ursprüngliche Registrierung erfolgt ist, denn
_Buttle_ weiß gar nicht, wie die `Driver` ursprünglich registriert
wurden (zumal ein `Class.forName` nur einmal zum Laden einer Klasse
und damit zum Ausführen der statischen Initialisierung führt).

Daher bleibt _Buttle_ nichts weiter übrig, als __dieselben__ `Driver`
Instanzen, die _Buttle_ zuvor gerade __deregistriert__ hat, umgehend
explizit selber wieder zu __re-registrieren__. Es kann nun aber sein,
dass die `Driver` Instanzen ihren Zustand geändert haben
(z.B. Ressourcen freigegeben haben) und in diesem Zustand gar nicht
korrekt als registrierter `Driver` funktionieren können, weil sie für
diesen Use-Case nie entworfen wurden.

Als weiteres Problem bleibt
`java.sql.DriverManager.getDrivers()`. Diese Methode liefert alle
registrierten `Driver`. Sollte die Anwendung diese Methode benutzen,
um sich selber einen _passenden_ `Driver` zu suchen, so würde sie auch
die _echten_ `Driver` (als _Kandidaten_) erhalten. Das kann oder kann
nicht zu dem gewünschten Programmverhalten führen. Als _Lösung_ könnte
_Buttle_ die `Driver` __nicht__ wie oben beschrieben _re-registrieren_
sondern stattdessen entweder

* Proxys um die originalen `Driver` (re-)registrieren oder
* gar keine `Driver` (re-)registrieren, sondern die _Auswahllogik_ wie
  oben erwähnt selber intern implemetieren.

In beiden Fällen bliebe aber dennoch ein letztes `getDrivers`-Problem:
es ist möglich, dass ein `Driver` registriert wird, __nachdem__
_Buttle_ die vorhandenen/registrierten `Driver` re-registriert hat
(denn dies geschieht ja nur einmalig, wenn _Buttle_ registriert
wird). _Buttle_ kann zwar immer wieder, wenn er angesprochen wird,
prüfen, ob seit der letzten/vorangegangenen Prüfung ein neuer `Driver`
hinzugekommen ist und diese dann auch wie oben beschrieben
ersetzen/re-registrieren. Aber es bleibt eine kleine Lücke, in der ein
neuer `Driver` registriert wird und unmittelbar anschließend
`getDrivers` aufgerufen wird. In so einem Szenario hat _Buttle_ keine
Möglichkeit, den neuen `Driver` zu ersetzen.

__Fazit:__ die Idee, dass eine Anwendung beim Einsatz von _Buttle_
weiterhin eine JDBC-URL des _echten_ JDBC-Treibers verwendet, wirft
eine Menge Fragen und mögliche Problemsituationen auf.

Es sollte also wenn möglich auf diese Option verzichtet werden.

## Zugriff auf echten JDBC-Treiber

Da _Buttle_ wie beschrieben die registrierten `Driver` alle im
`DriverManager` belässt bzw. _re-registriert_, kann _Buttle_ den
`DriverManager` weiterhin dafür benutzen, zu einer _echten_ JDBC-URL
den _echten_ Driver zu finden.

Dazu ruft _Buttle_ einfach
`java.sql.DriverManager.getDriver(<jdbc-url>)` auf und muss dann nur
merken, wenn _Buttle_ selber gerade vom `DriverManager` aufgerufen
wurde - ansonsten würde es zu einer Endlosrekursion kommen.

_Buttle_ erhält somit vom `DriverManager` den _echten_ `Driver` und
kann selber einen `Driver`-Proxy zu diesem liefern.

# Beispiele




# Notizen

* Buttle erweitern

* Buttle hacken

* Driver Interface: was liefern die einzelnen Methoden?

* Mehrere JDBC Verbindungen um verschiedene JDBC Verbindungen?

* Buttle als XATreiber?

* Buttle im App-Server vor einer DataSource?

