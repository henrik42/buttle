import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ButtleTest {

	public static void processEvent(Object e) {
		System.out.println("event : " + e);
	}

	public static void main(String[] args) throws Exception {

		// System.setProperty("buttle.user-form", "(load-file \"examples/buttle/examples/event_channel.clj\")");
		// System.setProperty("buttle.user-form", "(load-file \"examples/buttle/examples/java_events.clj\")");
		// System.setProperty("buttle.user-form", "(load-file \"examples/buttle/examples/handle.clj\")");
		
		// needs -Dbuttle_jaeger_agent_host=<buttle_jaeger_agent_host>
		System.setProperty("buttle.user-form", "(load-file \"examples/buttle/examples/open_tracing.clj\")");

		// -Dbuttle_user=<buttle_user> -Dbuttle_password=<buttle_password>
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
				// Watch out - output will be mixed up with output from Clojure
				// buttle.examples.event-channel/handle-with-log
				System.out.print(rs.getObject(i));
			}
			System.out.println();
		}
	}
}