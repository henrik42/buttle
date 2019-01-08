import java.sql.*;

class ButtleTest {

	public static void main(String[] args) throws Exception {

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