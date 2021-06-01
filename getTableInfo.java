package getTableInfo;

import java.sql.*;
import java.util.*;

public class getTableInfo {
	public static int getTableList(ArrayList<String> TableList, Connection conn, String DBuser) {
		int tCount = 0;

		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
		
		String		tableName = null;
					
		try {
			stmt = conn.createStatement();
				
			sql = "select class_name from db_class";
			sql += " where is_system_class = 'NO'";
			sql += "   and class_type = 'CLASS'";
			if (!DBuser.toUpperCase().equals("DBA"))
				sql += "   and owner_name = '" + DBuser.toUpperCase() + "'";
			sql += " order by class_name";
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				tCount++;
				tableName = rs.getString("class_name");
				TableList.add(tableName);
			}
			rs.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				tCount = -1;
				e.printStackTrace();
			}
		}
		
		return tCount;	
	}

	public static void main(String[] args) {
		if (args.length < 4) {
			System.out.println("Usage: getTabelInfo <hostname/IP> <DB name> <DB USER> <DB PW>");
			System.exit(0);
		}

		String		URL = null;
		String		DBname = null, DBuser = null;
		Connection	conn = null;	
		
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;

		String				tableName = null;
		ArrayList<String>	TableList = new ArrayList<String>();
					
		int		tCnt = 0, i = 0;
		int		rCnt = 0;
		long	trCnt = 0;

		try {
			Class.forName("cubrid.jdbc.driver.CUBRIDDriver");

			DBname = args[1];
			DBuser = args[2];			
			URL = "jdbc:cubrid:" + args[0] + ":33000:" + DBname + ":::";

			conn = DriverManager.getConnection(URL, DBuser, args[3]);
			
			if ((tCnt = getTableList(TableList, conn, DBuser)) < 0) {
				conn.close();
				System.exit(0);
			}
			
			stmt = conn.createStatement();
			for (i = 0; i < TableList.size(); i++) {
				tableName = (String)TableList.get(i);
				System.out.print("  - " + tableName + ": ");
				
				sql = "select count(*) cnt from [" + tableName + "]";
				rs = stmt.executeQuery(sql);
				if (rs.next()) {
					rCnt = rs.getInt("cnt");
					System.out.println(rCnt);
					trCnt += rCnt;
				}
				rs.close();
			}
			stmt.close();

			System.out.println("");
			System.out.println(DBname + " Table count: " + tCnt);			
			System.out.println("  - Total record count: " + trCnt);
		} catch(ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
				if (conn != null) conn.close();
			} catch (SQLException e) {
			}
		}
	}
}
