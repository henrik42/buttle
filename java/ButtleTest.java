// user@foo:~/solo/buttle/java$ java -Djdbc.drivers=buttle.jdbc.Driver:org.postgresql.Driver -cp ../src/:../target/classes/:../target/buttle-0.1.0-SNAPSHOT.jar:/home/user/.m2/repository/org/clojure/clojure/1.8.0/clojure-1.8.0.jar:../resources/postgresql-9.4-1201-jdbc41.jar:. ButtleTest

import java.sql.*;

class ButtleTest {

    public static void main(String[] args) throws Exception {

        if (false) {
            System.out.println("Loading driver");
            Driver loadedDriver = (Driver) Class.forName("buttle.jdbc.Driver").newInstance();
            System.out.println("Loaded driver " + loadedDriver);
            
            // DriverManager.registerDriver(loadedDriver);
            
            Driver driver = DriverManager.getDriver("jdbc:postgres:foobar");
            System.out.println("JAVA: driver " + driver);
            
            driver.connect("jdbc:postgres:foobar", null);
        }

        Connection conn = DriverManager
            .getConnection("jdbc:buttle:{:user \"inno\" :password \"inno\" :delegate-url \"jdbc:postgresql://127.0.0.1:5432/hhe\"}");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("select * from foobar");

        while (rs.next()) {
            Object o = rs.getObject(1);
            System.out.println("O=" + o);
        }

    }
}