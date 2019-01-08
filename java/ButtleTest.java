import java.sql.*;

import clojure.lang.Compiler;
import clojure.lang.RT;

class ButtleTest {

	public static void main(String[] args) throws Exception {

		String user = System.getProperty("buttle_user");
		String password = System.getProperty("buttle_password");
		String jdbcUrl = "jdbc:postgresql://127.0.0.1:6632/postgres";
		String buttleUrl = String.format("jdbc:buttle:{:user \"%s\" :password \"%s\" :target-url \"%s\"}", user,
				password, jdbcUrl);

		Connection conn = DriverManager.getConnection(buttleUrl, user, password);

		// re-defmethod buttle.proxy/handle :default so that proxy'ed method calls get
		// printed to STDOUT
		Compiler.loadFile("examples/buttle/examples/event_channel.clj");

		Statement stmt = conn.createStatement();
		ResultSet rs = stmt.executeQuery("select * from pg_catalog.pg_tables where schemaname = 'pg_catalog'");

		for (int cc = rs.getMetaData().getColumnCount(); rs.next();) {
			for (int i = 1; i <= cc; i++) {
				System.out.print(i == 1 ? "" : ",");
				// Watch out - output will be mixed up with output from Clojure
				// buttle.examples.event-channel/handle-with-log
				System.out.print(rs.getObject(i));
			}
			System.out.println();
		}
	}
}