import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.PreparedStatement;

public class URLShortnerDB {
	private static Connection connect(String url) {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url);
			/**
			 * pragma locking_mode=EXCLUSIVE;
			 * pragma temp_store = memory;
			 * pragma mmap_size = 30000000000;
			 **/
			String sql = """
			pragma synchronous = normal;
			pragma journal_mode = WAL;
			""";
			Statement stmt  = conn.createStatement();
			stmt.executeUpdate(sql);

		} catch (SQLException e) {
			System.out.println(e.getMessage());
        	}
		return conn;
	}

	private Connection conn=null;
	public URLShortnerDB(){ this("jdbc:sqlite:/virtual/henriq93/example.db"); }
	public URLShortnerDB(String url){ conn = URLShortnerDB.connect(url); }

			   
	public String find(String shortURL) {
		try {
			Statement stmt  = conn.createStatement();
			String sql = "SELECT longurl FROM bitly WHERE shorturl=?;";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1,shortURL);
			ResultSet rs = ps.executeQuery();

			if(rs.next()) return rs.getString("longurl");
			else return null; 

		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		return null;
	}

	public boolean save(String shortURL,String longURL){
		// System.out.println("shorturl="+shortURL+" longurl="+longURL);
		try {
			String insertSQL = "INSERT INTO bitly(shorturl,longurl) VALUES(?,?) ON CONFLICT(shorturl) DO UPDATE SET longurl=?;";
			PreparedStatement ps = conn.prepareStatement(insertSQL);
			ps.setString(1, shortURL);
			ps.setString(2, longURL);
			ps.setString(3, longURL);
			ps.execute();

			return true;

		} catch (SQLException e) {
			System.out.println(e.getMessage());
			return false;
		}
	}
}
