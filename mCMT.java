/*
 * 사용법 : README_first.txt 참고
 *
 * Created by JWNAM on 2019-8-9 for ORALCE. But currently not supported.
 * Modified by JWNAM on 2020-9,10,11 for Altibase, Tibero.
 * Modified by JWNAM on 2021-05 for DB2.
 * 
 * Copyright 2019~ CUBRID. All Rights Reserved.
 */

package mCMT;

import java.util.Properties;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.math.*;
//import java.nio.charset.Charset;

class Key {
	String				constraintName;
	int					constraintId;
	int					tableId;
	int					userId;
	String				keyColumns;
	ArrayList<String>	columnList;
	String				refTableName;
	int					refIndexId;
	String				refConstraintName;
	ArrayList<String>	refColumnList;
	int					deleteRule;
	
	public Key() {
		constraintName = null;
		constraintId = 0;
		tableId = 0;
		userId = 0;
		keyColumns = null;
		columnList = null;
		refTableName = null;
		refIndexId = 0;
		refConstraintName = null;
		refColumnList = null;
		deleteRule= 0;
	}
}

class Index {
	String		indexName;
	String		indexSchema;
	String		isUnique;
	int			indexId;
	int			tableId;
	int			userId;
	
	public Index() {
		indexName = null;
		indexName = null;
		isUnique = "F";
		indexId = 0;
		tableId = 0;
		userId = 0;
	}
}

class TableInfo implements Comparable<TableInfo> {
	String	tableName;
	int		recordCount;
	
	public TableInfo() {
		tableName = null;
		recordCount = -1;
	}
	
	public int compareTo(TableInfo arg0) {
		// return this.tableName.compareTo(tableName);
		if (recordCount < arg0.recordCount)
			return 1;
		else if (recordCount > arg0.recordCount)
			return -1;
		else
			return 0;
	}	
}

public class mCMT {
	static int				RECORD_LIMIT = 5000;  // 이 건수를 초과하고 blob 가 있는 경우 별도의 blob file 을 만들 수 있음.
	static int				MAX_STRING_LENGTH = 70; // unloaddb objects 와 동일한 길이
	static int				MAX_BUFFER_SIZE = MAX_STRING_LENGTH * 100;
	static int				MAX_BLOB_BUFFER_SIZE = 128 * 1024 * 1024;
	static int				freeSpacePercent = 10;  // 디스크 잔여공간 비율
	static String			aliasRownum = "__r_num__";
	static String			SEPERATOR = "_|^|^|_";
	static String			LOB_TYPE = "LoB";
	
	static String			outputDir = ".";
	static String			outputPrefix = null;
	
	static String			versionStr = "20210531";
	
	static String			srcIP = null;
	static String			srcDB = null;
	static String			srcDBname = null;
	static String			srcDBuser = null;
	
	static Connection		DBconn = null;
	
	static boolean			withoutBlob = false;
	static String			exportTable = null;
	static String			whereClause = null;

	static FileWriter		fwCheckListPK = null;
	static FileWriter		fwCheckListDefault = null;
	static FileWriter		fwCheckListFloat = null;
	static FileWriter		fwCheckListNumber = null;
	static FileWriter		fwCheckListBlob = null;
	static BufferedWriter	bwCheckListPK = null;
	static BufferedWriter	bwCheckListDefault = null;
	static BufferedWriter	bwCheckListFloat = null;
	static BufferedWriter	bwCheckListNumber = null;
	static BufferedWriter	bwCheckListBlob = null;

	// 변환도중 자동으로 하기에 무리가 있는 것에 대하여 사용자가 확인할 수 있도록 로그를 만들어 줌
	public static void makeCheckList(String type, String msg) {
		try {
			if (type.equals("PK")) {
					bwCheckListPK.write(msg);
					bwCheckListPK.newLine();
					bwCheckListPK.flush();
			} else if (type.equals("Default")) {
					bwCheckListDefault.write(msg);
					bwCheckListDefault.newLine();
					bwCheckListDefault.flush();
			} else if (type.equals("Float")) {
					bwCheckListFloat.write(msg);
					bwCheckListFloat.newLine();
					bwCheckListFloat.flush();
			} else if (type.equals("Number")) {
					bwCheckListNumber.write(msg);
					bwCheckListNumber.newLine();
					bwCheckListNumber.flush();
			} else if (type.equals("Blob")) {
				bwCheckListBlob.write(msg);
				bwCheckListBlob.newLine();
				bwCheckListBlob.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String getPrefix(String hostinfo) {
		String	findStr = ".";
		int	idx1 = hostinfo.indexOf(findStr);
		
		if (idx1 > 0) {
			int idx2 = hostinfo.indexOf(findStr, idx1+1);
			return hostinfo.substring(idx2+1);
		}
		
		return hostinfo;
	}
	public static boolean isColumnExist(String tName, String cName) {
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
					
		int					i = 0;
		int					metaCount = 0;
		ResultSetMetaData	metaInfo = null;
		
		try {
			stmt = DBconn.createStatement();
			sql = "select * from system_.sys_columns_ where rownum = 1";
			
			rs = stmt.executeQuery(sql);
		
			metaInfo = rs.getMetaData();
			 
			metaCount = metaInfo.getColumnCount();
			for (i = 1; i <= metaCount; i++) {
				if (cName.toLowerCase().equals(metaInfo.getColumnName(i).toLowerCase())) {
					rs.close();
					stmt.close();
					return true;
				}
			}
			
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return false;
	}
	// table 목록 화일이 있는 경우는 화일로 부터 읽어들임.
	public static int getTableListFromFile(String tFile, ArrayList<String> TableList) {
		int tCount = 0;
		
		FileReader		fr = null;
		BufferedReader	buf = null;
		try {
			fr = new FileReader(tFile);
			buf = new BufferedReader(fr);
			
			String	line = null;
				
			while ((line = buf.readLine()) != null)
				TableList.add(line.toUpperCase());
			buf.close();
		} catch (FileNotFoundException e) {
			System.out.println("File open error: " + tFile);
			tCount = -1;
		} catch (IOException e) {
			System.out.println("File read error: " + tFile);
			tCount = -1;
		} finally {
			try {
				if (buf != null) buf.close();
				if (fr != null) fr.close();
			} catch (IOException e) {
				tCount = -1;
				e.printStackTrace();
			}
		}
		
		return tCount;		
	}

	// table 목록 가져옴
	public static int getTableList(String tableType, ArrayList<String> TableList) {
		int tCount = 0;

		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
		
		int			len = 0;
		String		tableName = null;
					
		try {
			stmt = DBconn.createStatement();
				
			if (srcDB.equals("altibase")) {
				sql = "alter session set query_timeout=0";
				stmt.executeUpdate(sql);
				
				sql = "select t.table_name";
				sql += " from system_.sys_tables_ t, system_.sys_users_ u";
				if (tableType.equals("V"))
					sql += " , system_.sys_views_ v";
				sql += " where u.user_id > 1";
				sql += "   and u.user_name = '" + srcDBuser + "'";
				sql += "   and t.user_id = u.user_id";
				sql += "   and t.table_type = '" + tableType + "'";
				//sql += " and t.table_name like 'TB_HZ272_%'";
				if (isColumnExist("system_.sys_tables_", "hidden"))
					sql += "  and t.hidden = 'N'";	
				if (tableType.equals("V")) {
					sql += "  and t.table_id = v.view_id";
					sql += "  and v.status = 0";
				}
				sql += " order by t.table_name";
			} else if (srcDB.equals("tibero")) {
				sql = "select object_name table_name from all_objects";
				if (tableType.equals("T")) {
					sql += " where object_type = 'TABLE'";
				} else if (tableType.equals("V")) {
					sql += " where object_type = 'VIEW'";
				}
				sql += "   and owner = '" + srcDBuser + "'";
				sql += "   and status = 'VALID'";
				if (tableType.equals("T"))
					sql += "   and temporary = 'N'";
				sql += " order by table_name";
			} else if (srcDB.contentEquals("db2")) {
				sql = "select tabname table_name from syscat.tables";
				if (tableType.equals("T")) {
					sql += " where type = 'T'";
				} else if (tableType.equals("V")) {
					sql += " where type = 'V'";
					// function based index 를 만들면 view 도 만들어지기때문에 그렇게 만들어진 view 는 제거하기 위함.
					sql += "   and tabname not in (select viewname from syscat.indexes where owner = '" + srcDBuser + "' and uniquerule != 'P' and viewname is not null)";
				}
				sql += "   and owner = '" + srcDBuser + "'";
				sql += "   and tabschema != 'SYSTOOLS'";
				sql += "   and status = 'N'";
				sql += " order by table_name";
			}
			
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				tableName = rs.getString("table_name");
				if (srcDB.equals("altibase")) {
					len = tableName.length();
					// dictionary 에는 대문자로 저장된다. 그런데 소문자가 있다는 것은 정상테이블이 아님.
					if (len > 4 && tableName.substring(len - 4).equals("_bak")) {
						System.out.println("    --- Non normal Table: " + tableName);
						continue;
					} else	if (tableName.charAt(0) >= '0' && tableName.charAt(0) <= '9') {
						System.out.println("    --- Non normal Table: " + tableName);
						continue;
					}
				}

				tCount++;
				TableList.add(tableName);
			}
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

	public static int getPrecision(String tableName, String columnName) {
		int			precision = -1;
		String		val = null;
		
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
		try {
			stmt = DBconn.createStatement();
			
			sql = "select max(" + columnName + ") max_val";
			sql += " from " + tableName;
			rs = stmt.executeQuery(sql);
			rs.next();
			val = rs.getString("max_val");
			if (rs.wasNull())
				precision = -1;
			else
				precision = val.indexOf(".");
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return precision;
	}
	
	public static String convertType(String data_type, int data_length, int data_precision, int data_scale) {
		String	dataType = null;
		
		if (data_type.equals("BIT")) {
				if (data_length > 1) dataType = "bit(" + data_length + ")";
					else dataType = "bit";
		} else if (data_type.equals("VARBIT")) {
				if (data_length > 1) dataType = "bit varying(" + data_length + ")";
					else dataType = "bit varying";
		} else if (data_type.equals("BYTE")) {
				if (data_length > 1) dataType = "byte(" + data_length + ")";
				else dataType = "byte";
		} else if (data_type.equals("NCHAR")
					|| data_type.equals("CHAR")
					|| data_type.equals("CHARACTER")
					|| data_type.equals("GRAPHIC")) {
				dataType = "char(" + data_length + ")";
		} else if (data_type.equals("VARCHAR")
					|| data_type.equals("NVARCHAR")
					|| data_type.equals("NVARCHAR2")
					|| data_type.equals("VARCHAR2")
					|| data_type.equals("LONG VARCHAR")
					|| data_type.equals("VARGRAPHIC")
					|| data_type.equals("LONG VARGRAPHIC")) {
				dataType = "varchar(" + data_length + ")";
		} else if (data_type.equals("LONG")) {
				dataType = "/*long" + LOB_TYPE + "*/ string";
		} else if (data_type.equals("CLOB")) {
				dataType = "/*c" + LOB_TYPE + "*/ string";
		} else if (data_type.equals("BLOB")) {
			dataType = "/*b" + LOB_TYPE + "*/ bit varying(1073741823)";
		} else if (data_type.equals("RAW")) {
			dataType = "/*raw" + LOB_TYPE + "*/ bit varying(1073741823)"; // tibero
		} else if (data_type.equals("NUMERIC")
				|| data_type.equals("NUMBER")
				|| data_type.equals("DECIMAL")) {
				if (data_precision == -1 && data_scale == 0) dataType = "int";
				else {
					dataType = "numeric";					
					if (srcDB.equals("tibero") 
							&& data_precision == -1 && data_scale == -1) { // float 였던 것으로 추정
						dataType += "(38,10) /*FLOAT?*/";
					} 
					if (data_precision != -1) {
						dataType += "(" + data_precision;
						if (data_scale > 0) dataType += "," + data_scale;
						dataType += ")";
					}	
				}
		} else if (data_type.equals("INTEGER")) {
			dataType = "int";
		} else if (data_type.equals("DECFLOAT")) {
			dataType = "double /*DECFLOAT*/";
		} else if (data_type.equals("DATE")) {
			if (srcDB.equals("db2"))
					dataType = "date";
			else
				dataType = "datetime";
		} else if (data_type.equals("TIMESTAMP") && srcDB.equals("db2")) {
				dataType = "datetime";
		} else if (data_type.indexOf("TIMESTAMP") > -1) {
				dataType = "timestamp";
		} else {
				dataType = data_type.toLowerCase();
		}
		
		return dataType;
	}

	public static String convertDefault(String val, String valType) {
		String DefaultValue = null, tmpDefaultValue = null;

		if (val.substring(val.length()-1).equals("\n"))
			tmpDefaultValue = val.substring(0, val.length() - 1);
		else
			tmpDefaultValue = val;
		
		if (valType.equals("char") || valType.equals("varchar")) {
				DefaultValue = tmpDefaultValue;
		} else if (valType.equals("number") || valType.equals("smallint") || valType.equals("int") || valType.equals("bigint")) {
				if (tmpDefaultValue.substring(0, 1).equals("'"))
					DefaultValue = tmpDefaultValue.substring(1, tmpDefaultValue.length() - 1);
				else
					DefaultValue = tmpDefaultValue;
		} else if (valType.equals("datetime")) {
				if (tmpDefaultValue.substring(0, 1).equals("'"))
					DefaultValue = tmpDefaultValue;
				else {
					if (tmpDefaultValue.indexOf("SYSDATE") > -1)
						DefaultValue = tmpDefaultValue.replaceAll("SYSDATE", "SYSDATETIME");
					else if (tmpDefaultValue.indexOf("CURRENT TIMESTAMP") > -1)
						DefaultValue = tmpDefaultValue.replaceAll("CURRENT TIMESTAMP", "SYSDATETIME");
				}
		} else if (valType.equals("date")) {
			if (tmpDefaultValue.substring(0, 1).equals("'"))
				DefaultValue = tmpDefaultValue;
			else
				DefaultValue = tmpDefaultValue.replaceAll("CURRENT DATE", "SYSDATE");
		} else if (valType.equals("time")) {
			if (tmpDefaultValue.substring(0, 1).equals("'"))
				DefaultValue = tmpDefaultValue;
			else
				DefaultValue = tmpDefaultValue.replaceAll("CURRENT TIME", "SYSTIME");
		} else {
				DefaultValue = tmpDefaultValue;
		}

		return DefaultValue;
	}
	
/*
 * refOptionList 상에 set null 이 있을때 해당 FK 에 대한 column 이름과 일치하느지 확인
 */
	public static boolean checkFKSetnull(String columnName, ArrayList<String> keyList, ArrayList<String> refOptionList) {
		String	refOption = null;
		String	keyString = null;
		String[]	column = null;
		
		int	c = 0;
		for (int i = 0; i < refOptionList.size(); i++) {
			refOption = (String)refOptionList.get(i);
			if (refOption.equals("SET NULL")) {
				// keyList 에 FK 가 먼저있으므로 refOption 순서와 같다.
				keyString = (String)keyList.get(i);
				if (keyString.substring(0, 2).equals("FK")) {
					column = keyString.split("[(, )]+");
					// keyString 의 처음 문자열은 PK/FK 구분과 key이름이므로 2개는 제외
					for (c = 2; c < column.length; c++) {
						if (column[c].equals("[" + columnName.toUpperCase() + "]"))
							return true;
					}
				}		
			}
		}
		return false;
	}

	/*
	 * 키 목록 가져옴. 기본키/외래키
	 * return: 키개수
	 */
	public static int getKeyList(String tableName, char keyType, ArrayList<Key> KeyList) {
		int	keyCnt = 0;

		int		i = 0;
		int		constraintType = 0;
		
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;

		String	columnName = null;
		
		Key					KeyElement = null;
		ArrayList<String>	columnList = null;

		try {
			// 키 기본 정보 가져옴.
			stmt = DBconn.createStatement();
			if (srcDB.equals("altibase")) {
				if (keyType == 'P') constraintType = 3;
				else if (keyType == 'F') constraintType = 0;

				sql = "select c.constraint_name, c.constraint_id, c.delete_rule, u.user_id, t.table_id";
				sql += ", (select table_name from system_.sys_tables_ t where t.table_id = c.referenced_table_id) reference_table";
				sql += ", c.referenced_index_id, '' r_constraint_name";	
				sql += " from system_.sys_constraints_ c, system_.sys_tables_ t, system_.sys_users_ u";
				sql += " where c.constraint_type = " + constraintType;
				sql += " and u.user_name = '" + srcDBuser + "' and t.table_name = '" + tableName + "'";
		  		sql += " and u.user_id = c.user_id";
		  		sql += " and t.table_id = c.table_id";
			} else if (srcDB.equals("tibero")) {
				sql = "select f.constraint_name, -999 constraint_id";
				sql += ", case when delete_rule = 'CASCADE' then 1";
				sql += "       when delete_rule = 'SET NULL' then 2";
				sql += "       else 3 end delete_rule";
				sql += ", -999 user_id, -999 table_id";
				sql += ", (select p.table_name from all_constraints p where f.r_constraint_name = p.constraint_name and p.constraint_type = 'P') reference_table";
				sql += ", -999 referenced_index_id, r_constraint_name";
				sql += " from all_constraints f"; 
				sql += " where owner = '" + srcDBuser.toUpperCase() + "' and table_name = '" + tableName.toUpperCase() + "'";
				if (keyType == 'P')
					sql += " and constraint_type = 'P'"; 
				else if (keyType == 'F')
					sql += " and constraint_type = 'R'"; 
			} else if (srcDB.equals("db2")) {
				sql = "select tf.constname constraint_name, -999 constraint_id";
				sql += ", case when r.deleterule = 'C' then 1";
				sql += "       when r.deleterule = 'N' then 2";
				sql += "       else 3 end delete_rule";
				sql += ",999 user_id, -999 table_id";
				sql += ", r.reftabname reference_table";
				sql += ", -999 referenced_index_id, r.refkeyname r_constraint_name";
				sql += " from syscat.tabconst tf left outer join syscat.references r";
				sql += "   on tf.constname = r.constname";
				sql += " where tf.tabname = '" + tableName.toUpperCase() + "'";
				sql += " and tf.tabschema = (select tabschema from syscat.tables";
				sql += "       where tabname = '" + tableName.toUpperCase() + "' and type = 'T'";
				sql += "         and owner = '" + srcDBuser + "' and tabschema != 'SYSTOOLS' and status = 'N')";
				sql += " and tf.owner = '" + srcDBuser + "'";
				sql += " and tf.type = '" + keyType + "'";
			}
			rs = stmt.executeQuery(sql);

			while (rs.next()) {
				KeyElement = new Key();
				
				KeyElement.constraintName = rs.getString("constraint_name");
				KeyElement.constraintId = rs.getInt("constraint_id");
				KeyElement.deleteRule = rs.getInt("delete_rule");
				KeyElement.refTableName = rs.getString("reference_table");
				KeyElement.refIndexId = rs.getInt("referenced_index_id");
				KeyElement.refConstraintName = rs.getString("r_constraint_name");
				KeyElement.userId = rs.getInt("user_id");
				KeyElement.tableId = rs.getInt("table_id");
					
				KeyList.add(KeyElement);
			}
			rs.close();
			stmt.close();			

			// 키 세부 정보 가져옴
			keyCnt = KeyList.size();
			for (i = 0; i < keyCnt; i++) {
				KeyElement = KeyList.get(i);
				columnList = new ArrayList<String>();
				
				stmt = DBconn.createStatement();
				// 키 컬럼 리스트
				if (srcDB.equals("altibase")) {
					sql = "select col.column_name";
					sql += " from system_.sys_constraint_columns_ c, system_.sys_columns_ col";
					sql += " where c.constraint_id = " + KeyElement.constraintId;
					sql += "   and c.user_id = " + KeyElement.userId;
					sql += "   and c.table_id = " + KeyElement.tableId;
					sql += "  and c.column_id = col.column_id";
					sql += " order by c.constraint_col_order";
				} else if (srcDB.equals("tibero")) {
					sql = "select column_name"; 
					sql += " from all_cons_columns"; 
					sql += " where owner = '" + srcDBuser.toUpperCase() + "' and table_name = '" + tableName.toUpperCase() + "'";
					sql += " and constraint_name = '" + KeyElement.constraintName + "'"; 
					sql += " order by position";				
				} else if (srcDB.equals("db2")) {
					sql = "select colname column_name";
					sql += " from syscat.keycoluse"; 
					sql += " where tabname = '" + tableName.toUpperCase() + "'";
					sql += " and constname = '" + KeyElement.constraintName + "'"; 
					sql += " order by colseq";				
				}			

				rs = stmt.executeQuery(sql);
	
				while (rs.next()) {
					columnName = rs.getString("column_name");
					
					if (KeyElement.keyColumns == null)
						KeyElement.keyColumns = "[" + columnName + "]";
					else
						KeyElement.keyColumns += ", [" + columnName + "]";

					columnList.add(columnName);		
				}
				rs.close();
				stmt.close();
	
				KeyElement.columnList = columnList;
	
				// 외래키 참조 테이블 컬럼 정보 가져옴
				if (keyType == 'F') {
					columnList = new ArrayList<String>();

					stmt = DBconn.createStatement();
					if (srcDB.equals("altibase")) {
						sql = "select col.column_name";
						sql += " from system_.sys_index_columns_ ic, system_.sys_columns_ col";
						sql += " where ic.index_id = " + KeyElement.refIndexId;
						sql += " and ic.column_id = col.column_id";
						sql += " order by ic.index_col_order";
					} else if (srcDB.equals("tibero")) {
						sql = "select column_name"; 
						sql += " from all_cons_columns"; 
						sql += " where owner = '" + srcDBuser.toUpperCase() + "' and table_name = '" + KeyElement.refTableName + "'";
						sql += " and constraint_name = '" + KeyElement.refConstraintName + "'";
						sql += " order by position";				
					} else if (srcDB.equals("db2")) {
						sql = "select colname column_name";
						sql += " from syscat.keycoluse"; 
						sql += " where tabname = '" + KeyElement.refTableName + "'";
						sql += " and constname = '" + KeyElement.refConstraintName + "'"; 
						sql += " order by colseq";				
					}			
					rs = stmt.executeQuery(sql);
		
					while (rs.next()) {
						columnList.add(rs.getString("column_name"));		
					}
					rs.close();
					stmt.close();
		
					KeyElement.refColumnList = columnList;
					KeyList.set(i, KeyElement);
				}
			}
		} catch (SQLException e) {
			keyCnt = -1;
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				keyCnt = -1;
				e.printStackTrace();
			}
		}
		
		return keyCnt;
	}

	/*
	 * 컬럼 목록을 가져옮. 
	 * return: 컬럼개수
	 */
	public static int getColumnList(String tableName, ArrayList<String> ColumnList) {
		int		columnCount = 0;

		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;

		int		columnId = 0;
		String	columnType = null;
		String	columnName = null;
		String	dataType = null;
		int		dataLength = -1, dataPrecision = -1, dataScale = -1;
		String	columnDefault = null;
		String	defaultValue = null;
		String	columnIsnull = null;
		
		int		userId = 0;
		int		tableId = 0;

		String		columnInfo = null;
		try {
			stmt = DBconn.createStatement();
			if (srcDB.equals("tibero")) {
				sql = "select column_id, column_name, -999 user_id, -999 table_id";
				sql += ", data_type, data_length, data_precision, data_scale";
				sql += ", nullable, data_default"; 
				sql += " from all_tab_columns";
				sql += " where owner = '" + srcDBuser + "' and table_name = '" + tableName + "'";
				sql += " order by column_id";
			} else if (srcDB.equals("altibase")) {
				sql = "select c.column_id, c.column_name, u.user_id, t.table_id";
				sql += "      , case c.data_type when  1 then 'CHAR'";
				sql += "                         when 12 then 'VARCHAR'";
				sql += "                         when -8 then 'NCHAR'";
				sql += "                         when -9 then 'NVARCHAR'";
				sql += "                         when  2 then 'NUMERIC'";
				sql += "                         when  6 then 'FLOAT'";
				sql += "                         when  8 then 'DOUBLE'";
				sql += "                         when  7 then 'REAL'";
				sql += "                         when -5 then 'BIGINT'";
				sql += "                         when  4 then 'INT'";
				sql += "                         when  5 then 'SMALLINT'";
				sql += "                         when  9 then 'DATE'";
				sql += "                         when 30 then 'BLOB'";
				sql += "                         when 40 then 'CLOB'";
				sql += "                         when 20001 then 'BYTE'";
				sql += "                         when 20002 then 'NIBBLE'";
				sql += "                         when -7 then 'BIT'";
				sql += "                         when -100 then 'VARBIT'";
				sql += "                         when 10003 then 'GEOMETRY' end data_type";
				sql += "      , c.precision data_length, c.precision data_precision, c.scale data_scale";
				sql += "      , decode(c.is_nullable, 'F', 'N', 'Y') nullable, c.default_val data_default"; 
				sql += " from system_.sys_tables_ t, system_.sys_users_ u, system_.sys_columns_ c";
				sql += " where u.user_name = '" + srcDBuser + "' and t.table_name = '" + tableName + "'";
				sql += "  and t.user_id = u.user_id";
				sql += "  and t.user_id = c.user_id";
				sql += "  and t.table_id = c.table_id";	
				if (isColumnExist("system_.sys_columns_", "is_hidden"))
					sql += "  and c.is_hidden = 'F'"; // function based index 가 있을 경우, 이 컬럼이 생겨며 값이 'T'	
				sql += " order by c.column_order";
			} else if (srcDB.equals("db2")) {
				sql = "select colno column_id, colname column_name, -999 user_id, -999 table_id";
				sql += "   , typename data_type ,length data_length ,length data_precision ,scale data_scale";
				sql += "   , nulls nullable, default data_default";
				sql += " from syscat.columns"; 
				sql += " where tabname = '" + tableName + "'";
				sql += "   and tabschema = (select tabschema from syscat.tables"; 
				sql += "					where tabname = '" + tableName + "' and type = 'T'";
				sql += "					  and owner = '" + srcDBuser + "'";
				sql += "					  and tabschema != 'SYSTOOLS' and status = 'N')";
				sql += " order by colno";
			}
			
			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				columnCount++;
			
				columnId = rs.getInt("column_id");
				columnName = rs.getString("column_name");
				
				dataType = rs.getString("data_type");
				dataLength = rs.getInt("data_length");
			
				dataPrecision = rs.getInt("data_precision");
				if (rs.wasNull()) dataPrecision = -1;
				dataScale = rs.getInt("data_scale");
				if (rs.wasNull()) dataScale = -1;
			
				columnIsnull = rs.getString("nullable");		
				columnDefault = rs.getString("data_default"); // 문자형에 대한 default 가 상수일경우 ' 를 포함하고 있다. ' 가 없으면 함수로 볼 수 있음.
				
				if (srcDB.equals("altibase") && dataType.equals("BYTE")) {
					columnId = rs.getInt("column_id");
					userId = rs.getInt("user_id");
					tableId = rs.getInt("table_id");

					Statement	stmt0 = DBconn.createStatement();
					sql = "select column_id";
					sql += " from system_.sys_constraint_columns_"; 
					sql += " where constraint_id = ("; 
					sql += "	select constraint_id";
					sql += "	from system_.sys_constraints_ c";
					sql += "	where c.user_id = " + userId;
					sql += "	and c.table_id = " + tableId;
					sql += "	and c.constraint_type = 5";
					sql += " )";
					
					ResultSet	rs0 = stmt0.executeQuery(sql);
					if (rs0.next()) {
						if (columnId == rs0.getInt("column_id")) 
							dataType = "TIMESTAMP";
					}
					
					rs0.close();
					stmt0.close();
				}
			
				columnType = convertType(dataType, dataLength, dataPrecision, dataScale);
			
				columnInfo = "[" + columnName.toLowerCase() + "]\t" + columnType;
				if (columnIsnull.toUpperCase().equals("N")) {
					// 외래키에서 set null 로 설정되어있다면 해당 컬럼을 nullable 로 해야한다.
					/* from 10.x 필요없어보임.
					if (fkCnt == 0 || checkFKSetnull(columnName, keyList, refOptionList) == false)
						bw.write(" not null");
					*/
					columnInfo += " not null";
				}
				if (columnDefault != null) {
					defaultValue = convertDefault(columnDefault, columnType);
					if (!defaultValue.equals(""))
						columnInfo += " default " + defaultValue;
					
					// default 값에 대하여 CUBRID 와 다른 부분들이 있을수 있어 이에 대한 checklist 를 저장.
					makeCheckList("Default", tableName + "(" + columnName + " " + columnType + ") " + columnDefault + " -> " + defaultValue);
				}
				
				ColumnList.add(columnInfo);
			} // while (rs.next()) ;
				
			
			rs.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return columnCount;
	}

/*
 * table 정보를 가져옴. column 속성을 먼저 가져오고, key, unique, index 정보를 다음에 가져옴
 * DDL (create table) 생성
 * 테이블명 등은 소문자로 변경
 * keyList: [PK/FK 키이름(컬럼리스트)] String 을 만들어 List 에 넣음. 키는 unique index 와 중복여부 확인을 위해 여기선 목록만 만들고 index 처리시 같이 처리
 */
	public static int exportTable(String tableName, BufferedWriter bw, ArrayList<Key> PKeyList, ArrayList<Key> FKeyList, String exportType, ArrayList<Integer> rCountHasLob) {
		int		i = 0;
		int		keyCnt = 0, columnCnt = 0;
		boolean	hasLob = false;
		
		ArrayList<String>	ColumnList = new ArrayList<String>();

		try {
			// 외래키정보 가져옴
			if (getKeyList(tableName, 'F', FKeyList) < 0)
				return -1;
			
			// 컬럼 정보 가져옴. 키 정보등은 아래에서 따로 가져옴.
			if ((columnCnt = getColumnList(tableName, ColumnList)) < 0)
				return -1;
			
			bw.write("create table [" + tableName.toLowerCase() + "]");
			if (columnCnt > 0) {
				bw.write(" (");
				bw.newLine();

				for (i = 0; i < columnCnt; i++) {					
					bw.write("	" + ColumnList.get(i));
					if (i+1 < columnCnt)
						bw.write(",");
					bw.newLine();
					
					if (!hasLob && ColumnList.get(i).matches("(.*)" + LOB_TYPE + "(.*)"))
						hasLob = true;
				}
				bw.write(");");
			}
			bw.newLine();
			bw.flush();
			
			if (hasLob)
				rCountHasLob.add(0);
			else
				rCountHasLob.add(-1);

			// 기본키 정보 가져옴.
			keyCnt = getKeyList(tableName, 'P', PKeyList);
			if (keyCnt  < 0)
				return -1;
			else if (keyCnt == 0)
				makeCheckList("PK", "NO Primary key : " + tableName.toLowerCase());

		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return columnCnt;
	}

/*
 * View 정보를 가져옴.
 * 이름 등은 소문자로 변경
 */
	public static int exportView(BufferedWriter bw) {
		int		viewCount = 0;
	
		int		i = 0;
		int		colCount = 0;
		
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
	
		ArrayList<String>	ViewList = new ArrayList<String>();
		ArrayList<String>	ColumnList = new ArrayList<String>();

		String	viewName = null;
		
		try {
			if ((viewCount = getTableList("V", ViewList)) <= 0)
				return viewCount;

			stmt = DBconn.createStatement();
			for (i = 0; i < viewCount; i++) {
				viewName = ViewList.get(i);
				// altibase 는 view 생성시 컬럼 리스트를 지정하지 않는다.
				if (srcDB.equals("tibero") || srcDB.equals("db2")) {
					if ((colCount = getColumnList(viewName, ColumnList)) < 0) {
						if (stmt != null) stmt.close();
						return -1;
					}
					
					bw.write("create view [" + viewName.toLowerCase() + "]");
					if (colCount > 0) {
						bw.write(" (");
						bw.newLine();
	
						for (i = 0; i < colCount; i++) {					
							bw.write("	" + ColumnList.get(i));
							if (i+1 < colCount)
								bw.write(",");
							bw.newLine();
						}
						bw.write(")");
					}
					bw.newLine();
					bw.flush();
				}
				
				// view spec
				if (srcDB.equals("altibase")) {
					sql = "select p.parse";
					sql += " from system_.sys_users_ u, system_.sys_tables_ t, system_.sys_view_parse_ p";
					sql += " where u.user_name = '" + srcDBuser + "'";
					sql += "   and u.user_id = t.user_id";
					sql += "   and t.table_name = '" + viewName + "'";
					sql += "   and t.table_id = p.view_id";
					sql += " order by seq_no";
				} else if (srcDB.equals("tibero")) {
					sql = "select text parse from all_views";
					sql += " where owner = '" + srcDBuser + "'";
					sql += "   and view_name = '" + viewName + "'";
				} else if (srcDB.equals("db2")) {
					sql = "select text parse from syscat.views";
					sql += " where viewname = '" + viewName + "'";
					sql += " and viewschema = (select tabschema from syscat.tables";
					sql += "       where tabname = '" + viewName + "' and type = 'V'";
					sql += "         and owner = '" + srcDBuser + "' and tabschema != 'SYSTOOLS' and status = 'N')";
					sql += " and owner = '" + srcDBuser + "'";
				}
				
				rs = stmt.executeQuery(sql);
				if (rs.next()) {
					do {
						bw.write(rs.getString("parse")); 
						if (srcDB.equals("tibero")) bw.write(" ");
					} while (rs.next());
					
					bw.write(" ;");
					bw.newLine();
					bw.flush();
				} else {
					System.out.println(" - " + viewName + ": invalid view spec.");
					rs.close();
					stmt.close();
					return -1;
				}

			}
			
			rs.close();
			stmt.close();			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return viewCount;
	}		

/*
 * sequence 정보를 가져옴. cache 는 가져오긴하나 이관 대상에선 제외.
 * DDL (create serial) 생성
 * 이름 등은 소문자로 변경
 */
	public static int exportSequence(BufferedWriter bw) {
		int		seqCount = 0;
	
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;

		Statement	stmt0 = null;
		ResultSet	rs0 = null;
	
		String	seqName = null;
		long	seqCurrent, seqIncrement, seqStart;
		long	seqMin, seqMax;
		String	isCycle; 
//		int		cacheSize = 0;
		int		seqIsNull = 0;
		
		// sequence 가져옴
		try {
			stmt = DBconn.createStatement();
			if (srcDB.equals("altibase")) {
				sql = "select t.table_name seq_name, s.current_seq, decode(s.current_seq, null, 1, 0) seq_isnull";
				sql += "      , s.start_seq, s.increment_seq, s.min_seq, s.max_seq, s.is_cycle, s.cache_size";
				sql += " from v$seq s, system_.sys_tables_ t, system_.sys_users_ u";
				sql += " where u.user_name = '" + srcDBuser + "'";
				sql += "   and s.seq_oid = t.table_oid";
				sql += "   and t.user_id = u.user_id";
				sql += " order by seq_name";
			} else if (srcDB.equals("tibero")) {
				stmt0 = DBconn.createStatement();
				
				sql = "select sequence_name seq_name, -999 current_seq, 0 seq_isnull";
				sql += "      , min_value start_seq, increment_by increment_seq, min_value min_seq, max_value max_seq";
				sql += "      , decode(cycle_flag, 'Y', 'YES', 'NO') is_cycle, cache_size";
				sql += " from all_sequences";
				sql += " where sequence_owner = '" + srcDBuser + "'";
				sql += " order by seq_name";
			} else if (srcDB.equals("db2")) {
				stmt0 = DBconn.createStatement();
				
				sql = "select seqname seq_name, -999 current_seq, 0 seq_isnull";
				sql += "      , start start_seq, increment increment_seq, minvalue min_seq, maxvalue max_seq";
				sql += "      , decode(cycle, 'Y', 'YES', 'NO') is_cycle, cache cache_size";
				sql += " from syscat.sequences";
				sql += " where seqschema = '" + srcDBuser + "'";
				sql += " and owner = '" + srcDBuser + "'";
				sql += " order by seq_name";
			}
			
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				bw.newLine();
				bw.write("-- --------------"); bw.newLine();
				bw.write("-- Sequence"); bw.newLine();
				bw.write("-- --------------"); bw.newLine();

				do {
					seqCount++;
					
					seqName = rs.getString("seq_name");
					seqStart = rs.getLong("start_seq");
					seqCurrent = rs.getLong("current_seq");
					seqIncrement = rs.getLong("increment_seq");
					seqMin = rs.getLong("min_seq");
					seqMax = rs.getLong("max_seq");
					isCycle = rs.getString("is_cycle");
//					cacheSize = rs.getInt("cache_size");
					seqIsNull = rs.getInt("seq_isnull");

					// cubrid 는 started 를 기준으로 생성후 사용 여부를 판단하고, 사용전이면 nextval 시 current_val을 넘겨줌
					if(srcDB.equals("altibase")) {
						// altibase 의 경우 생성후 nextval 이 호출되지않으면 current_val 이 null 값임.
						if (seqIsNull == 1) {
							// 사용전이면 시작값(start_seq)을 현재값으로 넣어줌.
							seqCurrent = seqStart;
						} else {
							// 사용이 되었으므로 current value 를 넘겨줌.
							// 단 이관후 cubrid 에서 처음 사용이 되는 것으므로 current value 를 증가치 만큼 증가시켜놔야 한다.
							// 시작부터 currval 호출하면 값이 문제가 된다.....
							seqCurrent = seqCurrent + seqIncrement;
						}
					} else if (srcDB.equals("tibero")) {
						// 생성후 nextval 이 호출되지않았거나, session 연결후 처음 currval 이 호출되면 다음의 에러 발생.
						// TBR-6003: Sequence "SEQ" has not been accessed in this session: no CURRVAL available.
						// 따라서 정확한 현재값을 얻을 방법이 없으므로, nextval 로 값을 얻는다.
						// 어짜피 sequence 는 중복되지만 않으면 된다.
						// nextval 로 current 값을 가져오므로 시작여부 판단할 필요 없다.
						try {
							sql = "select " + seqName + ".nextval current_seq from dual";
							rs0 = stmt0.executeQuery(sql);
							if (rs0.next()) {
								seqCurrent = rs0.getLong("current_seq");
							}
							rs0.close();
						} catch (SQLException e) {
							if (e.getErrorCode() == -6004) // max 값에 도달했으며, nocycle 이어서 에러 발생.
								seqCurrent = seqMax;
							rs0.close();
						}
					} else if (srcDB.equals("db2")) {
						// 생성후 nextval 이 호출되지않았거나, session 연결후 처음 prevrval 이 호출되면 다음의 에러 발생.
						// PREVIOUS VALUE 표현식은 NEXT VALUE 표현식이 시퀀스 "SEQID = 8"에 대한 현재 세션에서 값을 생성하기 전에는 사용할 수 없습니다.. SQLCODE=-845
						// 따라서 정확한 현재값을 얻을 방법이 없으므로, nextval 로 값을 얻는다.
						// 어짜피 sequence 는 중복되지만 않으면 된다.
						// nextval 로 current 값을 가져오므로 시작여부 판단할 필요 없다.
						try {
							sql = "select (next value for " + seqName + ") current_seq from sysibm.dual";
							rs0 = stmt0.executeQuery(sql);
							if (rs0.next()) {
								seqCurrent = rs0.getLong("current_seq");
							}
							rs0.close();
						} catch (SQLException e) {
							if (e.getErrorCode() == -359) // max 값에 도달했으며, nocycle 이어서 에러 발생.
								seqCurrent = seqMax;
							rs0.close();
						}
					}
					
					bw.write("create serial [" + seqName.toLowerCase() + "]");
					bw.write(" start with " + seqCurrent);
					bw.write(" increment by " + seqIncrement);
					bw.write(" minvalue " + seqMin);
					if (!(srcDB.equals("aitibase") && seqMax == 9223372036854775806l) // altibase max 기본값
						&& !(srcDB.equals("tibero") && seqMax == 9223372036854775807l) // tibero max 기본값
						&& !(srcDB.equals("db2") && seqMax == 2147483647)) // db2 max 기본값
						bw.write(" maxvalue " + seqMax);
					if (isCycle.equals("YES"))
						bw.write(" cycle");
					else
						bw.write(" nocycle");
					// 전체적으로 cache 는 제품 관련 사항... 이것을 구지 설정해줘야하는가????
/*					
					if (cacheSize == 20) // altibase 기본값. cubrid 는 기본값이 cache 않함. 음....
						bw.write(" nocache");
					else
						bw.write(" cache " + cacheSize);
*/
					bw.write(";");
					bw.newLine();
					bw.flush();
				} while (rs.next()) ;
			} // end of if (rs.next())
			
			rs.close();
			stmt.close();			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
				if (rs0 != null) rs0.close();
				if (stmt0 != null) stmt0.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return seqCount;
	}
	
/*
 * Trigger 정보를 가져옴. trigger 문법이 상이하여 트리거 이름, 대상 테이블 정도의 정보만 가져온다.
 * 이름 등은 소문자로 변경
 */
	public static int exportTrigger(BufferedWriter bw) {
		int		triggerCount = 0;
	
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
	
		try {
			stmt = DBconn.createStatement();
			if (srcDB.equals("altibase")) {
				sql = "select tr.trigger_name, t.table_name, tr.is_enable";
				sql += " from system_.sys_users_ u, system_.sys_triggers_ tr, system_.sys_tables_ t";
				sql += " where u.user_name = '" + srcDBuser + "'";
				sql += "   and tr.user_id = u.user_id";
				sql += "   and tr.table_id = t.table_id";
				sql += " order by tr.trigger_name";
			} else if (srcDB.equals("tibero")) {
				sql = "select trigger_name, table_name, decode(status, 'ENABLE', 1, 0) is_enable";
				sql += " from all_triggers";
				sql += " where owner = '" + srcDBuser + "'";
				sql += " order by trigger_name";
			} else if (srcDB.equals("db2")) {
				sql = "select trigname trigger_name, tr.tabname table_name, decode(tr.valid, 'Y', 1, 0) is_enable";
				sql += " from syscat.triggers tr, syscat.tables t";
				sql += " where tr.owner = '" + srcDBuser + "'";
				sql += " and trigschema = t.tabschema";
				sql += " and t.tabname = tr.tabname and t.type = 'T'";
				sql += " and t.owner = tr.owner and t.tabschema != 'SYSTOOLS' and t.status = 'N'";
				sql += " order by trigger_name";
			}
			
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				bw.newLine();
				bw.write("-- --------------"); bw.newLine();
				bw.write("-- Trigger"); bw.newLine();
				bw.write("-- --------------"); bw.newLine();
				
				do {
					triggerCount++;
					
					bw.write("-- create trigger [" + rs.getString("trigger_name").toLowerCase() + "]");
					bw.write(" on " + rs.getString("table_name").toLowerCase() + " ......");
					bw.newLine();
					bw.flush();
				} while (rs.next()) ;
			} // end of if (rs.next())
			
			rs.close();
			stmt.close();			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return triggerCount;
	}
		

/*
 * StoredProcecure 정보를 가져옴. 문법이 상이하여 이름, procedure/function 정도의 정보만 가져온다.
 * 이름 등은 소문자로 변경
 */
	public static int exportStoredProcedure(BufferedWriter bw) {
		int		spCount = 0;
	
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
		
		int		objectType = 0;
	
		try {
			stmt = DBconn.createStatement();
			if (srcDB.equals("altibase")) {
				sql = "select p.proc_name, p.object_type";
				sql += " from system_.sys_users_ u, system_.sys_procedures_ p";
				sql += " where u.user_name = '" + srcDBuser + "'";
				sql += "   and p.user_id = u.user_id";
				sql += " order by proc_name";
			} else if (srcDB.equals("tibero")) {
				sql = "select object_name proc_name, decode(functionable, 'YES', 1, decode(interface, 'YES', 4, 0)) object_type";
				sql += " from all_procedures";
				sql += " where owner = '" + srcDBuser + "'";
				sql += " order by proc_name";
			} else if (srcDB.equals("db2")) {
				sql = "select routinename proc_name, (case when routinetype = 'P' then 0 when routinetype = 'F' then 1 else 6 end) object_type";
				sql += " from syscat.routines";
				sql += " where owner = '" + srcDBuser + "'";
				sql += " order by proc_name";
			}
			
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				bw.newLine();
				bw.write("-- --------------"); bw.newLine();
				bw.write("-- Stored Procedure"); bw.newLine();
				bw.write("-- --------------"); bw.newLine();
				
				do {
					spCount++;
					
					objectType = rs.getInt("object_type");
					
					bw.write("-- create ");
					if (objectType == 0)
						bw.write(" procedure ");
					else if (objectType == 1)
						bw.write(" function ");
					else if (objectType == 3) // for altibase
						bw.write(" type set ");
					else if (objectType == 4) // for tibero
						bw.write(" interface ");
					else if (objectType == 6) // for db2
						bw.write(" method ");

					bw.write(" " + rs.getString("proc_name").toLowerCase() + " ......");
					bw.newLine();
					bw.flush();
				} while (rs.next()) ;
			} // end of if (rs.next())
			
			rs.close();
			stmt.close();			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return spCount;
	}

public static boolean isKeyColumns(String indexColumns, ArrayList<Key> KeyList) {
		int	i = 0;
		int	keyCnt = 0;
		
		Key	KeyElement;
		
		keyCnt = KeyList.size();
		for (i = 0; i < keyCnt; i++) {
			KeyElement = KeyList.get(i);
			if (indexColumns.equals(KeyElement.keyColumns.toLowerCase()))
				return true;
		}

		return false;
	}
	
	public static int exportIndex(String tableName, BufferedWriter bw, BufferedWriter bwFindex, BufferedWriter bwPKey, BufferedWriter bwFKey, ArrayList<Key> PKeyList, ArrayList<Key> FKeyList) {
		int		indexCount = -1;

		Statement	stmt = null;
		PreparedStatement	pstmt = null;
		ResultSet	rs = null;
		String		sql = null;

		String	refColumns = null;
		String	onDelete = null;

		int		columnCnt = 0;	
		String	keyColumns = null;
		Key		KeyElement = new Key();
		
		int		i = 0, j = 0;
		int		columnIndex = 0;
		String	indexTypeName = null;
		String	indexColumns = null;
		String	columnNameT = null;
		String	columnName = null;
		
		int		defaultKeyPrefixLength = 0;
		String	defaultKeyPrefix = null;
		
		int		isFunctionBasedIndex = 0;
		String	indexDDL = null;
		
		Index				IndexElement = null;
		ArrayList<Index>	IndexList = new ArrayList<Index>();

		int		pkCnt = PKeyList.size();
		int		fkCnt = FKeyList.size();

		try {
			// 키 이름이 주어지지 않았을때 기본적으로 생성되는 키이름의 접두사 부분.
			if (srcDB.equals("altibase"))
				defaultKeyPrefix = "__SYS_CON_";
			else if (srcDB.equals("tibero"))
				defaultKeyPrefix = srcDBuser + "_CON";
			else if (srcDB.equals("db2"))
				defaultKeyPrefix = "SQL";
			defaultKeyPrefixLength = defaultKeyPrefix.length();

			// 기본키 정보 출력
			if (pkCnt > 0) {
				KeyElement = PKeyList.get(0);

				bwPKey.write("alter table [" + tableName.toLowerCase() + "] add");

				// 기본 접두사가 사옹된 경우는 키 이름을 별도로 입력하지 않은 경우이므로, 키 이름 없이 생성.
				if (!(KeyElement.constraintName.length() > defaultKeyPrefixLength
						&& KeyElement.constraintName.substring(0, defaultKeyPrefixLength).equals(defaultKeyPrefix)))
					bwPKey.write(" constraint [" + KeyElement.constraintName.toLowerCase() + "]");

				columnCnt = KeyElement.columnList.size();
				keyColumns = "[" + KeyElement.columnList.get(0) + "]";
				for (j = 1; j < columnCnt; j++) {
					keyColumns += ", [" + KeyElement.columnList.get(j) + "]";
				}
				
				bwPKey.write(" primary key(" + keyColumns.toLowerCase() + ");");

				bwPKey.newLine();
				bwPKey.flush();
			}
			
			// 외래키 정보 출력
			if (fkCnt > 0) {
				for (i = 0; i < fkCnt; i++) {
					KeyElement = FKeyList.get(i);

					bwFKey.write("alter table [" + tableName.toLowerCase() + "] add");
					
					if (!(KeyElement.constraintName.length() > defaultKeyPrefixLength
							&& KeyElement.constraintName.substring(0, defaultKeyPrefixLength).equals(defaultKeyPrefix)))
						bwFKey.write(" constraint [" + KeyElement.constraintName.toLowerCase() + "]");
					
					columnCnt = KeyElement.columnList.size();
					keyColumns = "[" + KeyElement.columnList.get(0) + "]";
					for (j = 1; j < columnCnt; j++) {
						keyColumns += ", [" + KeyElement.columnList.get(j) + "]";
					}
				
					bwFKey.write(" foreign key(" + keyColumns.toLowerCase() + ")");
					
					bwFKey.write(" references [" + KeyElement.refTableName.toLowerCase() + "]");
					columnCnt = KeyElement.refColumnList.size();
					refColumns = "[" + KeyElement.refColumnList.get(0).toLowerCase() + "]";
					for (j = 1; j < columnCnt; j++) {
						refColumns += ", [" + KeyElement.refColumnList.get(j).toLowerCase() + "]";
					}
					
					if (srcDB.equals("altibase")) {
						if (KeyElement.deleteRule == 1) onDelete = "cascade";
						else if (KeyElement.deleteRule == 2) onDelete = "set null";
						else onDelete = "no action";
					} else if (srcDB.equals("tibero")) {
						if (KeyElement.deleteRule == 1) onDelete = "cascade";
						else if (KeyElement.deleteRule == 2) onDelete = "set null";
						else onDelete = "no action";
					} else if (srcDB.equals("db2")) {
						if (KeyElement.deleteRule == 1) onDelete = "cascade";
						else if (KeyElement.deleteRule == 2) onDelete = "set null";
						else onDelete = "no action";
					}
					bwFKey.write("(" + refColumns + ") on delete " + onDelete.toLowerCase() + ";");
					
					bwFKey.newLine();
				}
				bwFKey.flush();
			}			
			
			// 인덱스 정보 가져옴. unique 도 같이 가져옴.
			stmt = DBconn.createStatement();
			if (srcDB.equals("tibero")) {
				sql = "select index_name, -999 index_id, 'IND_SCH' index_schema";
				sql += ", decode(uniqueness, 'UNIQUE', 'T', 'F') is_unique";
				sql += ", -999 user_id, -999 table_id";
				sql += " from all_indexes i";
				sql += " where table_owner = '" + srcDBuser.toUpperCase() + "' and table_name = '" + tableName.toUpperCase() + "'";
				sql += " and index_name not in (";
				sql += "    select constraint_name from all_constraints c";
				sql += "    where constraint_type in ('P', 'R') and c.owner = i.table_owner and c.table_name = i.table_name )";
				sql += " order by index_name";
			} else if (srcDB.equals("altibase")) {
				// unique 인 경우 PK 일 수 있다. 컬럼까지 뽑은 후 PK 와 비교해야 만 한다.
				sql = "select i.index_name, i.index_id, 'IND_SCH' index_schema";
				sql += ", i.is_unique, u.user_id, t.table_id";
				sql += " from system_.sys_indices_ i, system_.sys_users_ u, system_.sys_tables_ t";
				sql += " where u.user_name = '" + srcDBuser + "' and t.table_name = '" + tableName + "'";
				sql += "   and u.user_Id = i.user_id";
				sql += "   and t.table_id = i.table_id";
				sql += " order by i.index_name";
			} else if (srcDB.equals("db2")) {
				sql = "select indname index_name, -999 index_id, indschema index_schema";
				sql += ", decode(uniquerule, 'U', 'T', 'F') is_unique";
				sql += ", -999 user_id, -999 table_id";
				sql += " from syscat.indexes";
				sql += " where tabname = '" + tableName.toUpperCase() + "'";
				sql += " and tabschema = (select tabschema from syscat.tables";
				sql += "       where tabname = '" + tableName.toUpperCase() + "' and type = 'T'";
				sql += "         and owner = '" + srcDBuser + "' and tabschema != 'SYSTOOLS' and status = 'N')";
				sql += " and owner = '" + srcDBuser + "'";
				sql += " and uniquerule != 'P'";
			}

			rs = stmt.executeQuery(sql);
			while (rs.next()) {
				IndexElement = new Index();
				
				IndexElement.indexName = rs.getString("index_name");
				IndexElement.indexSchema = rs.getString("index_schema");
				IndexElement.indexId = rs.getInt("index_id");
				IndexElement.isUnique = rs.getString("is_unique");
				IndexElement.userId = rs.getInt("user_id");
				IndexElement.tableId = rs.getInt("table_id");
				
				IndexList.add(IndexElement);
			}
			rs.close();
			stmt.close();
			
			// 키 세부 정보 가져옴
			indexCount = IndexList.size();
			if (indexCount > 0) {
				if (srcDB.equals("altibase")) {
					sql = "select ic.index_col_order, c.column_name, ic.sort_order";
					sql += ", c.default_val func_expression";
					sql += " from system_.sys_index_columns_ ic, system_.sys_columns_ c";
					sql += " where ic.user_id = ?";
					sql += "   and ic.table_id = ?";
					sql += "   and ic.index_id = ?";
					sql += "   and ic.column_Id = c.column_Id";
					sql += " order by index_col_order";
				} else if (srcDB.equals("tibero")) {
					sql = "select i.column_position, i.column_name, decode(i.descend, 'DESC', 'D', 'A') sort_order";
					sql += ", e.column_expression func_expression";
					sql += " from all_ind_columns i left outer join all_ind_expressions e";
					sql += "   on i.table_owner = e.table_owner and i.table_name = e.table_name";
					sql += "  and i.index_name = e.index_name and i.column_position = e.column_position";
					sql += " where i.table_owner = '" + srcDBuser + "' and i.table_name = '" + tableName + "'";
					sql += " and i.index_name = ?";
					sql += " order by i.column_position";
				} else if (srcDB.equals("db2")) {
					sql = "select colseq column_position, colname column_name, colorder sort_order";
					sql += ", text func_expression";
					sql += " from syscat.indexcoluse";
					sql += " where indname = ?";
					sql += "   and indschema = ?";
					sql += " order by colseq";
				}

				pstmt = DBconn.prepareStatement(sql);

				for (i = 0; i < indexCount; i++) {
					IndexElement = IndexList.get(i);
	
					if (srcDB.equals("altibase")) {
						pstmt.setInt(1, IndexElement.userId);
						pstmt.setInt(2, IndexElement.tableId);
						pstmt.setInt(3, IndexElement.indexId);
					} else if (srcDB.equals("tibero")) {
						pstmt.setString(1, IndexElement.indexName);
					} else if (srcDB.equals("db2")) {
						pstmt.setString(1, IndexElement.indexName);
						pstmt.setString(2, IndexElement.indexSchema);
					}
					rs = pstmt.executeQuery();
					if (rs.next()) {
						columnIndex = 0;
						indexTypeName = "index";
						do {
							columnNameT = rs.getString("column_name").toLowerCase();
							if (srcDB.equals("tibero")) {
								if (columnNameT.equals("- expression column -")) {
									// function based index
									isFunctionBasedIndex = 1;
									// 컬럼명을 " 로 감싸고 있다.
									columnNameT = rs.getString("func_expression");
									columnName = columnNameT.replaceAll("\"", "'");
								} else {
									isFunctionBasedIndex = 0;
									columnName = "[" + columnNameT + "]";
								}
							} else if (srcDB.equals("altibase")) {
								if (columnNameT.indexOf(IndexElement.indexName.toLowerCase() + "$") > -1) {
									// function based index
									isFunctionBasedIndex = 1;
									columnName = rs.getString("func_expression").toLowerCase();
								} else {
									isFunctionBasedIndex = 0;
									columnName = "[" + columnNameT + "]";
								}
							} else if (srcDB.equals("db2")) {
								columnName = rs.getString("func_expression");
								if (columnName != null) {
									// function based index
									isFunctionBasedIndex = 1;
								} else {
									isFunctionBasedIndex = 0;
									columnName = "[" + columnNameT + "]";
								}
							}
							
							if (columnIndex == 0)
								indexColumns = columnName;
							else
								indexColumns += ", " + columnName;
							
							if (rs.getString("sort_order").equals("D"))
								indexColumns += " desc";
												
							columnIndex++;
						} while (rs.next());

						if (IndexElement.isUnique.equals("T")) {
							// PK 에 동일한 컬럼리스트가 있는지 확인
							if (isKeyColumns(indexColumns, PKeyList))
								indexTypeName = "EXIST";
							else 
								indexTypeName = "unique";
						} else {
							// FK 에 동일한 컬럼리스트가 있는지 확인
							if (isKeyColumns(indexColumns, FKeyList))
								indexTypeName = "EXIST";
						}
						
						if (!indexTypeName.equals("EXIST")) {
							indexDDL = "alter table [" + tableName.toLowerCase() + "] add";
							if (indexTypeName.equals("unique"))
								indexDDL += " unique(";
							else
								indexDDL += " index " + "[" + IndexElement.indexName.toLowerCase() + "](";
							indexDDL += indexColumns + ");";
							
							if (isFunctionBasedIndex == 1) {
								bwFindex.write(indexDDL);
								bwFindex.newLine();
								bwFindex.flush();
							} else {
								bw.write(indexDDL);
								bw.newLine();
								bw.flush();
							}
						}
					}
					rs.close();					
				}
				pstmt.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		return indexCount;
	}	

	public static int makeTableHeader(String tableName, int metaCount, ResultSetMetaData metaInfo, BufferedWriter bw) {
		int		i = 0;
		//int		p = 0, s = 0;
		String	columnName = null;
		String	buf = null;
		
		try {
			buf = "%class [" + tableName.toLowerCase() + "] (";
			 		
			for (i = 1; i <= metaCount; i++) {
				columnName = metaInfo.getColumnName(i).toLowerCase();
				if (!columnName.equals(aliasRownum))
					buf += "[" + columnName + "]";
				
				if (i < metaCount) buf += " ";
			}
			buf += ")\n";

			bw.write(buf);
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}

		return buf.length();
	}
	
	public static String byteToHexStr(byte[] byteStr, int start, int len) {
		int				i = start, end = start + len;
		StringBuilder	sb = new StringBuilder();
		
		for ( ; i < end; i++) {
			sb.append(Integer.toString((byteStr[i] & 0xFF) + 0x100, 16).substring(1));
		}
		
		return sb.toString();
	}
	
	public static boolean hasEnoughFreeSpace(int checkPercent, boolean printMsg) {
/*		
		File	curDir = new File("./");
		double	totSize = 0, freeSize = 0;
		int		freePercent = 0;

		totSize = curDir.getTotalSpace() / Math.pow(1024, 3);
		freeSize = curDir.getFreeSpace() / Math.pow(1024, 3);
		freePercent = (int)((freeSize / totSize) * 100);
		if (freePercent <= checkPercent) {
			if (printMsg)
				System.err.printf(" !!! At least %.1f G of space required.  %.2fG/%.2fG remains.", (totSize * checkPercent / 100), freeSize,  totSize);
			return false;
		}
*/		
		return true;
	}
	
	public static boolean appendBlobToData(BufferedWriter bw, String fnameBlobRecord, int lengthTableHeader) {
		FileInputStream	fis = null;

		byte	bufTableHeader[] = new byte[lengthTableHeader];
		byte	bufBlob[] = new byte[MAX_BUFFER_SIZE];
		int		readNo = 0;
		
		try {
			fis = new FileInputStream(fnameBlobRecord);
				
			readNo = fis.read(bufTableHeader);
			if (readNo < lengthTableHeader) {
				fis.close();
				return false;
			}
			while ((readNo = fis.read(bufBlob)) != -1) {
				bw.write(bufBlob.toString());
			}
		} catch (FileNotFoundException e) {
			System.out.println("File open error: " + fnameBlobRecord);
			return false;
		} catch (IOException e) {
			System.out.println("File read error: " + fnameBlobRecord);
			return false;
		} finally {
			try {
				if (fis != null) fis.close();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
		}

		return true;
	}
	public static int exportData(String tableName, String sCharset, String tCharset) {
		Statement	stmt = null;
		Statement	stmt0 = null;
		ResultSet	rs = null;
		ResultSet	rs0 = null;
		String		sql = null;
		
		int		i = 0, p = 0, s = 0;
		int		recordCount = 0, totalRecordCount = 0;
		int		progressPercent = 10;
		String	value = null;
		
		BigDecimal	valueBig = null;
		InputStream	is = null;
		Reader		ic = null;
		String		lobType = null;
		boolean		hasBlobMR = false; // RECORD_LIMIT 이상 데이터가 있고 blob 가 있는 경우. 데이터화일을 나누기 위해서 만들었으나 사용하지 않음
		int			bigBlobCnt = 0; // 128M 이상 blob 건수. 컬럼 기반.
		int			errBlobCnt = 0; // blob read 오류 건수. 알티의 경우 화일이 깨져도 오류처리가 않됨.
		
		int	 		clobLineNo = 0;
		int	 		blobLineNo = 0;
		char[]		clobData = new char[MAX_BUFFER_SIZE];
		byte[]		blobData = new byte[MAX_BLOB_BUFFER_SIZE];
		int			readLen = 0, maxReadLen = 0;

		int				idx = 0, maxStr = 0;
		StringBuilder	sb = new StringBuilder();
		
		int					metaCount = 0;
		ResultSetMetaData	metaInfo = null;
		int					lengthTableHeader = 0;

		String	metaType = null;
		String	convertedValue = null;

		String				fPrefix = outputPrefix + "__" + tableName;
		OutputStreamWriter	fwData = null;
		BufferedWriter		bw = null;
		
		String				fnameBlobRecord = null;
		OutputStreamWriter	fwBlob = null;
		BufferedWriter		bwBlob = null;
		
//		FileOutputStream	fs = null;

		long	beginTime = 0, endTime = 0;

		try {
			if (srcDB.equals("altibase")) {
				// altibase에서 blob select 시  LobLocator cannot span the transaction 에러 발생.
				DBconn.setAutoCommit(false);
			}

			stmt0 = DBconn.createStatement();
			if (srcDB.equals("altibase")) {
				stmt0.executeUpdate("alter session set query_timeout=0");
				stmt0.executeUpdate("alter session set fetch_timeout=0");
			}

			stmt = DBconn.createStatement();
			sql = "select count(*) cnt from " + tableName;

			rs = stmt.executeQuery(sql);
			rs.next();
			totalRecordCount = rs.getInt("cnt");
			System.out.print(":" + totalRecordCount);
			rs.close();
			
			if (totalRecordCount == 0) return 0;
			else {
				if (srcDB.equals("tibero")) {
					sql = "select data_type, count(*) lob_cnt"; 
					sql += " from all_tab_columns";
					sql += " where owner = '" + srcDBuser + "' and table_name = '" + tableName + "'";
					sql += " and data_type in ('BLOB', 'CLOB')";
					sql += " group by data_type";
				} else if (srcDB.equals("altibase")) {
					sql = "select decode(data_type, 30, 'BLOB', 40, 'CLOB') data_type, count(*) lob_cnt"; 
					sql += " from system_.sys_tables_ t, system_.sys_users_ u, system_.sys_columns_ c";
					sql += " where u.user_name = '" + srcDBuser + "' and t.table_name = '" + tableName + "'";
					sql += "  and t.user_id = u.user_id";
					sql += "  and t.user_id = c.user_id";
					sql += "  and t.table_id = c.table_id";	
					sql += " and c.data_type in (30, 40)"; // BLOB, CLOB
				} else if (srcDB.equals("db2")) {
					sql = "select typename data_type, count(*) lob_cnt"; 
					sql += " from syscat.columns";
					sql += " where tabname = '" + tableName.toUpperCase() + "'";
					sql += " and tabschema = (select tabschema from syscat.tables";
					sql += "       where tabname = '" + tableName.toUpperCase() + "' and type = 'T'";
					sql += "         and owner = '" + srcDBuser + "' and tabschema != 'SYSTOOLS' and status = 'N')";
					sql += " and typename in ('BLOB', 'CLOB')";
					sql += " group by typename";
				}
				rs = stmt.executeQuery(sql);
				while (rs.next()) {
					if (rs.getString("data_type").equals("BLOB"))
						lobType = "B";
					else
						lobType = "C";
					
					if (rs.getInt("lob_cnt") > 0) {
						System.out.print(lobType);
						if (lobType.equals("B")) hasBlobMR = false;
					} else {
						if (lobType.equals("B")) hasBlobMR = false;
					}
				}
				rs.close();
			}

			do { // 디스크 공간 부족 대비, 전체 레코드수 보다 적게 write 한 경우 해당 레코드부터 다시 검색하여 write 하도록 함.
				while (!hasEnoughFreeSpace(freeSpacePercent, true)) {
					System.err.print(" Make enough space, and press enter to contiue.");
					System.in.read();
					// read() 는 한번에 한 byte. 엔터를 입력해야 함. \r\n까지 읽어들이므로 기본 3byte(windows 의 경우)
					// close() 하면 finally 로 가버린다. 따라서 skip() 처리. input buffer에 남은것들 지워버리는 효과.
					System.in.skip(999999);					
				}

				if (recordCount == 0) {
					sql = "select t.* from " + tableName + " t";
				} else {
					// altibase, tibero 모두 rownum 이 1부터 시작됨. 따라서 inline view 를 이용하여 rownum 값을 가져올수밖에 없음.
					sql = "select t.* from ";
					sql += " ( select rownum " + aliasRownum + ", t0.* from " + tableName + " t0 ) t";
					sql += " where " + aliasRownum  + " >= " + (recordCount+1);
					System.err.println("SQL " + (recordCount+1) + "/" + totalRecordCount);
					}
				rs = stmt.executeQuery(sql);
	
				metaInfo = rs.getMetaData();
				metaCount = metaInfo.getColumnCount();
//* for all record	
				if (recordCount == 0)
					fwData = new  OutputStreamWriter(new FileOutputStream(fPrefix + ".data"), tCharset);
				else
					fwData = new  OutputStreamWriter(new FileOutputStream(fPrefix + "___" + (recordCount+1) + ".data"), tCharset);
				bw = new BufferedWriter(fwData);

				if ((lengthTableHeader = makeTableHeader(tableName, metaCount, metaInfo, bw)) < 0) {
					return -1;
				}
//*/				
				// session 유지위해 1분간격으로 질의 수행. 이를 위해 시작 시간 구함.
				beginTime = System.currentTimeMillis();
				// number 로만 선언된 컬럼은 확인가능하므로, precision 최대값과 scale 최대값을 알아낼수 있고, 이를 checklist file 에 추가해주자.
				while (rs.next()) {
					if (hasBlobMR) {
						fnameBlobRecord = fPrefix + "___" + (recordCount+1) + ".blob.data";
						fwBlob = new  OutputStreamWriter(new FileOutputStream(fnameBlobRecord), tCharset);
						bwBlob = new BufferedWriter(fwBlob);
						if ((lengthTableHeader = makeTableHeader(tableName, metaCount, metaInfo, bwBlob)) < 0) {
							return -1;
						}
					}
/* for each record					
					if (recordCount == 0)
						fwData = new  OutputStreamWriter(new FileOutputStream(fPrefix + ".data"), tCharset);
					else {
						if (bw != null) bw.close();
						if (fwData != null) fwData.close();
						fwData = new  OutputStreamWriter(new FileOutputStream(fPrefix + "___" + (recordCount+1) + ".data"), tCharset);
					}
					bw = new BufferedWriter(fwData);
					if ((metaCount = makeTableHeader(tableName, metaCount, metaInfo, bw)) < 0) {
						return -1;
					}
*/
					if (hasBlobMR)
						bwBlob.write(++recordCount + ":");
					else
						bw.write(++recordCount + ":");

					for (i = 1; i <= metaCount; i++) {
						// 디스크 공간부족시 레코드를 나누어 가져오게 되므로, rownum 값은 제외시킴
						if (metaInfo.getColumnName(i).toLowerCase().equals(aliasRownum)) continue;

						rs.getObject(i); // getObject() 는 null 을 반환하지 않으며, wasNull() 을 위해 호출함.
						if (rs.wasNull()) {
							if (hasBlobMR)
								bwBlob.write(" NULL");
							else
								bw.write(" NULL");
							
							continue;
						}
						
						metaType = metaInfo.getColumnTypeName(i);
						if (!withoutBlob && metaType.equals("BLOB")) {
							if (srcDB.equals("altibase")) {
								Blob	sBlob = rs.getBlob(i);
								is = sBlob.getBinaryStream();
							} else if (srcDB.equals("tibero")) {
								is = rs.getBinaryStream(i); // BLOB 는 getString() 시도시 SQLException: Type mismatch 발생
							} else if (srcDB.equals("db2")) {
								Blob	sBlob = rs.getBlob(i);
								is = sBlob.getBinaryStream();
							}
						} else if (withoutBlob && metaType.equals("BLOB")) {
							value = "NULL"; // 그냥 의미상 넣어둘뿐 이값을 사용하진 않음.
						} else if (metaType.equals("CLOB")) {
							if (srcDB.equals("altibase")) {
								Clob	sClob = rs.getClob(i);
								ic = sClob.getCharacterStream();
							} else if (srcDB.equals("tibero")) {
								ic = rs.getCharacterStream(metaInfo.getColumnName(i));
							} else if (srcDB.equals("db2")) {
								Clob	sClob = rs.getClob(i);
								ic = sClob.getCharacterStream();
							}
						} else
							value = rs.getString(i);
						
						if (metaType.equals("CHAR") || metaType.equals("VARCHAR") || metaType.equals("VARCHAR2")
								|| metaType.equals("NCHAR") || metaType.equals("NVARCHAR") || metaType.equals("NVARCHAR2")
								|| metaType.equals("LONG")) {
								convertedValue = value;
								// 출력된 문자열의 길이가 MAX_STRING_LENGTH 초과하면
								if (convertedValue.length() > MAX_STRING_LENGTH) {
									int		valLength = convertedValue.length();
									int		beginIdx = 0, endIdx = MAX_STRING_LENGTH;
									String	val = null;
									
									while (endIdx <= valLength) {
										val = convertedValue.substring(beginIdx, endIdx);
										
										if (hasBlobMR)
											bwBlob.write(" '" + val.replace("'", "''") + "'");
										else
											bw.write(" '" + val.replace("'", "''") + "'");
										
										beginIdx += MAX_STRING_LENGTH;
										endIdx += MAX_STRING_LENGTH;
										if (beginIdx >= valLength) break;
										if (endIdx >= valLength) endIdx = valLength;

										if (hasBlobMR) {
											bwBlob.write("+");
											bwBlob.newLine();
										} else {
											bw.write("+");
											bw.newLine();
										}
									}
								} else {
									if (hasBlobMR)
										bwBlob.write(" '" + convertedValue.replace("'", "''") + "'");
									else
										bw.write(" '" + convertedValue.replace("'", "''") + "'");
								}
								
						} else if (metaType.equals("NUMBER") || metaType.equals("NUMERIC")) {
								if (hasBlobMR) {
									if (value.charAt(0) == '.') {
										if (value.length() > 38) {
											bwBlob.write(" 0" + value.substring(0, 38));
											makeCheckList("Number", tableName + "(" + metaInfo.getColumnName(i) + " number) " + value + " is truncated to " + value.substring(0, 38));
										} else
											bwBlob.write(" 0" + value);
									} else {
										if (value.length() > 38) {
											bwBlob.write(" " + value.substring(0, 38));
											makeCheckList("Number", tableName + "(" + metaInfo.getColumnName(i) + " number) " + value + " is truncated to " + value.substring(0, 38));
										} else
											bwBlob.write(" " + value);
									}
								} else {
									if (value.charAt(0) == '.') {
										if (value.length() > 38) {
											bw.write(" 0" + value.substring(0, 38));
											makeCheckList("Number", tableName + "(" + metaInfo.getColumnName(i) + " number) " + value + " is truncated to " + value.substring(0, 38));												
										} else
											bw.write(" 0" + value);
									} else {
										if (value.length() > 38) {
											bw.write(" " + value.substring(0, 38));
											makeCheckList("Number", tableName + "(" + metaInfo.getColumnName(i) + " number) " + value + " is truncated to " + value.substring(0, 38));
										} else
											bw.write(" " + value);
									}
								}
								
								p = metaInfo.getPrecision(i);
								s = metaInfo.getScale(i);
								if (srcDB.equals("tibero")) {
									if (p == 38 && s == 0) {
										// 자리수가 없다면 float 였을 수 있음.
										// bigdicimal 로 받아야 소수점 이하 숫자가 정확하다. 바로 숫자로 받으면 소수점이후 일부 숫자 잘림.
										valueBig = rs.getBigDecimal(i);
										value = valueBig.toString();
										idx = value.indexOf(".");
										if (idx != -1) // 소수점이 있다면
											makeCheckList("Float", tableName + "(" + metaInfo.getColumnName(i) + " number) " + value);
									}
								}
						} else if (metaType.equals("DATE") || metaType.equals("TIMESTAMP")) {
								if (hasBlobMR)
									bwBlob.write(" '" + value + "'");
								else
									bw.write(" '" + value + "'");
						} else if (metaType.equals("CLOB")) {
								idx = 0;
								maxStr = 0;
								sb.setLength(0);
								
								clobLineNo = 0;
								while ((readLen = ic.read(clobData, 0, MAX_BUFFER_SIZE)) >= 0) { // 약 1.4M 읽음
									if (clobLineNo == 0) sb.append(" '"); // 제일 처음일 경우만.
									idx = 0; clobLineNo = 0; //한번 읽은 버퍼에 대하여 line no
									while (idx < readLen) {
										maxStr = (++clobLineNo) * MAX_STRING_LENGTH;
										while (idx < readLen && idx < maxStr) {
											if (clobData[idx] == '\'')
												sb.append('\'');
											sb.append(clobData[idx++]);
										}
										// 최대 라인만큼 처리했거나, 그렇지 않았더라도 읽기를 MAX_STRING_LENGTH 배수만큼이므로 문자열을 닫고 
										// 더 있을 수 잇으모로 + 표시하고 다음 라인을 위해 문자열을 열어준다.
										sb.append("'+\n '");
									}
								}
								// 읽은 만큼 다 처리했으므로, 그리고 앞서 문자열을 열어두었으므로 닫아주기만 한다. 
								sb.append("' ");
								ic.close();
								if (hasBlobMR)
									bwBlob.write(sb.toString());
								else
									bw.write(sb.toString());
								
								sb.setLength(0);
						} else if (metaType.equals("BLOB")) {
								if (withoutBlob) {
									if (hasBlobMR)
										bwBlob.write(" NULL");
									else
										bw.write(" NULL");
								} else {
									idx = 0;
									maxStr = 0;
									sb.setLength(0);
									try {
										blobLineNo = 0;
										maxReadLen = 0;
										// byte 를 hex 형태의 asc code로 표시하면 2자리 문자열이 된다. 
										while ((readLen = is.read(blobData)) > 0) {
											maxReadLen += readLen;
											if (maxReadLen >= 128 * 1024 * 1024) {
												System.err.println("WARNING:BigBlob(128M) rec:" + recordCount + "-" + maxReadLen + "/" + readLen);
												makeCheckList("Blob", tableName + "(" + metaInfo.getColumnName(i) + ") BigBlob(128M) rec:" + recordCount + "-" + maxReadLen + "/" + readLen);												
												bigBlobCnt++;
												if (maxReadLen == readLen) {
													// 처음읽은 개수가 128M 이상인 경우... 보통은 network 상황때문에 조금씩 여러번 가져온다. 그래서 NULL 로 설정이 어렵다.
													if (hasBlobMR)
														bwBlob.write(" NULL");
													else
														bw.write(" NULL");
													
												}
												break;
											}
											
											if (blobLineNo++ == 0) {
												if (hasBlobMR)
													bwBlob.write(" X'");
												else
													bw.write(" X'");
											}
											idx = 0;
											while (idx < readLen) { // 읽은 개수만큼 byte 문자열로 변환
												sb.setLength(0); // 문자열 버퍼 초기화
												// data 화일 한 라인의 최대 문자수(MAX_STRING_LENGTH) 만큼씩 처리하기 위함.
												// idx 는 읽어들인 버퍼상의 데이터 위치. 읽어들인 버퍼의 크기는 한 데이터화일 한 라인보다 클 수 있으므로
												// maxStr 은 한 라인 분량을 처리하기 위한 최대 개수만큼의 버퍼 위치. 한 라인 쓰기 시작하기전 라인 최대수 만큼 증가되어야 함.
												// byte 문자열로 변환하면 2배가 되므로 실제 처리할 문자열을 1/2
												maxStr = idx + (MAX_STRING_LENGTH/2); 
												while (idx < readLen && idx < maxStr) { // byte 문자열로 변환
													sb.append(Integer.toString((blobData[idx++] & 0xFF) + 0x100, 16).substring(1));
												}
												// 최대 라인만큼 처리했거나, 그렇지 않았더라도 읽기를 MAX_STRING_LENGTH 배수만큼이므로 문자열을 닫고 
												// 더 있을 수 잇으모로 + 표시하고 다음 라인을 위해 문자열을 열어준다.
												sb.append("'+\n '");
		
												if (hasBlobMR)
													bwBlob.write(sb.toString());
												else
													bw.write(sb.toString());
											}
											if (hasBlobMR)
												bwBlob.write("' ");
											else
												bw.write("' ");													
										}
										// 읽은 만큼 다 처리했으므로, 그리고 앞서 문자열을 열어두었으므로 닫아주기만 한다. 
										is.close();
									} catch (IOException e) {
										errBlobCnt++;
										makeCheckList("Blob", tableName + "(" + metaInfo.getColumnName(i) + ") ERR: " + recordCount + "[" + rs.getString("file_id") + "] - " + e.toString());												
										e.printStackTrace();
									}
								}
						} else {
								if (hasBlobMR)
									bwBlob.write(" " + value); 
								else
									bw.write(" " + value);
						}
					} // end of for (i = 1; i <= metaCount; i++)
					
					if (hasBlobMR) {
						bwBlob.newLine();
						bwBlob.close();
					} else {
						bw.newLine();
						bw.flush();
					}

					// blob 데이터 라인이 MAX_STRING_LENGTH 와 ( ''+\n) 을 추가하고 있으므로					
					if (hasBlobMR && (blobLineNo * 77) <= (200 * 1024 * 1024)) { // Blob 기준 200M.?
						if (appendBlobToData(bw, fnameBlobRecord, lengthTableHeader)) {
							File delFile = new File(fnameBlobRecord);
							delFile.delete();
						}
					}
					
					if (!hasEnoughFreeSpace(freeSpacePercent, false)) {
						// 디스크 공간이 부족하므로 record fetch 중단.
						// 공간 보족 메세지는 찍을 필요없음. 다음 loop 에서 찍으므로.
						System.err.println("--- " + recordCount + "/" + totalRecordCount + "exported.");
						break;
					}
					
					if ((recordCount * 100 / totalRecordCount) >= progressPercent) {
						if (progressPercent == 10)
							System.out.print("-");
						System.out.print(progressPercent/10);
						progressPercent += 10;
					}

					endTime = System.currentTimeMillis();
					if ((endTime - beginTime) >= 50000) {
						// session 유지를 위해 1분정도 간격으로(50초 넘었으면) 질의 한번씩 수행시킴.
						// 다음 fetch 시 blob 처리로 인해 시간 오래걸릴 수 있으므로.
						beginTime = endTime;
						rs0 = stmt0.executeQuery("select 1 from dual");
						rs0.close();
					}
				} // end of while(rs.next())
				
				rs.close();

				bw.close();
				fwData.close();
			} while (recordCount < totalRecordCount);
			
			if (bigBlobCnt > 0)
				System.out.print("[BigBlob(>=128M):" + bigBlobCnt + "]");
			if (errBlobCnt > 0)
				System.out.print(" -E:" + errBlobCnt + " ");
			
		} catch (IOException e) {
			System.err.println("record:" + recordCount + ", Blobline:" + blobLineNo);
			recordCount = -1;
			e.printStackTrace();
		} catch (SQLException e) {
			recordCount = -1;
			e.printStackTrace();
		} finally {
			try {
				if (bw != null) bw.close();
				if (fwData != null) fwData.close();
				if (srcDB.equals("altibase")) {
					DBconn.rollback();
					DBconn.setAutoCommit(true);
				}
				if (rs != null) rs.close();
				if (rs0 != null) rs0.close();
				if (stmt != null) stmt.close();
				if (stmt0 != null) stmt0.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return recordCount;
	}

	// record 개수 구함
	public static int getRecordCount(String tableName) {
		Statement	stmt = null;
		ResultSet	rs = null;
		String		sql = null;
				
		int		cnt = 0;
		try {
			stmt = DBconn.createStatement();
				
			sql = "select count(*) cnt from " + tableName;
			
			rs = stmt.executeQuery(sql);
			if (rs.next())
				cnt = rs.getInt("cnt");
			else
				cnt = 0;
			
			rs.close();
			stmt.close();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null) rs.close();
				if (stmt != null) stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		
		return cnt;	
	}

	public static boolean makeTableListFile(ArrayList<String> TableList, ArrayList<Integer> rCountHasLob, String ExportType) {
		int	cntLobTable = rCountHasLob.size();
		int	i = 0, idx = 0;
		int	TABLE_LIST_COUNT = 5;
		ArrayList<TableInfo>	tableInfoList = new ArrayList<TableInfo>();
		TableInfo				tableInfo = null;
		
		for (i = 0; i < cntLobTable; i++) {
			tableInfo = new TableInfo();
			
			tableInfo.tableName = TableList.get(i);
			tableInfo.recordCount = rCountHasLob.get(i);
			
			tableInfoList.add(tableInfo);
		}

		try {
			FileWriter[]		fw = new FileWriter[TABLE_LIST_COUNT];
			BufferedWriter[]	bw = new BufferedWriter[TABLE_LIST_COUNT];

			
			for (i = 0; i < TABLE_LIST_COUNT; i++) {
				fw[i] = new FileWriter(outputPrefix + ".table_" + (i+1) + ".list");
				bw[i] = new BufferedWriter(fw[i]);
			}
			
			if (ExportType.equals("tablelist")) // 이미 테이블 이름 순이므로, -S 일경우만 blob 포함한 테이블의 레코드수를 기준으로 정렬
				Collections.sort(tableInfoList);
			
			while (idx < cntLobTable) {
				for (i = 0; i < TABLE_LIST_COUNT; i++) {
					tableInfo = tableInfoList.get(idx++);
					
					bw[i].write(tableInfo.tableName);
					bw[i].newLine();
					
					if (idx >= cntLobTable) break;
				}
			}

			for (i = 0; i < 5; i++) {
				bw[i].close();
				fw[i].close();
			}
		} catch (IOException e) {
			
		}
		return true;
	}
	
/* 
 * table, index, data, serial, view, trigger
 */
	public static boolean exportDB(ArrayList<String> TableList, String ExportType, String sCharset, String tCharset) {
		// 출력화일 open, 기존 화일 덮어씀.
		boolean	rtn = true;
		int		exportCount = -1;
		long	totalExportedData = 0;
		ArrayList<Integer>	rCountHasLob = new ArrayList<Integer>();
		
		String	tableName = null;

		OutputStreamWriter	fwTable = null;
		OutputStreamWriter	fwView = null;
		OutputStreamWriter	fwPKey = null;
		OutputStreamWriter	fwFKey = null;
		OutputStreamWriter	fwIndex = null;
		OutputStreamWriter	fwFxIndex = null;
		OutputStreamWriter	fwEtc = null;

		BufferedWriter	bwTable = null;
		BufferedWriter	bwView = null;
		BufferedWriter	bwPKey = null;
		BufferedWriter	bwFKey = null;
		BufferedWriter	bwIndex = null;
		BufferedWriter	bwFxIndex = null;
		BufferedWriter	bwEtc = null;
		
		// index 생성시 PK, FK 중복 여부 확인시도 필요함.
		ArrayList<Key>	PKeyList = new ArrayList<Key>();
		ArrayList<Key>	FKeyList = new ArrayList<Key>();

		long	beginTime = System.currentTimeMillis();
		
		try {
			if (ExportType.equals("schema") || ExportType.equals("tablelist") || ExportType.equals("all")) {
				fwTable = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".table"), tCharset);
				bwTable = new BufferedWriter(fwTable);

				fwView = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".view"), tCharset);
				bwView = new BufferedWriter(fwView);
				
				fwPKey = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".pk"), tCharset);
				bwPKey = new BufferedWriter(fwPKey);

				fwFKey = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".fk"), tCharset);
				bwFKey = new BufferedWriter(fwFKey);

				fwIndex = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".index"), tCharset);
				bwIndex = new BufferedWriter(fwIndex);

				fwFxIndex = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".findex"), tCharset);
				bwFxIndex = new BufferedWriter(fwFxIndex);

				fwEtc = new OutputStreamWriter(new FileOutputStream(outputPrefix + ".etc"), tCharset);
				bwEtc = new BufferedWriter(fwEtc);
			}

			for (int i = 0; i < TableList.size(); i++) {
				tableName = (String)TableList.get(i);

				System.out.print(" - exporting " + (i+1) + "/" + TableList.size() + " [" + tableName + "] ");

				if (ExportType.equals("all") || ExportType.equals("schema")|| ExportType.equals("tablelist")) {
					System.out.print("Columns");
					exportCount = exportTable(tableName, bwTable, PKeyList, FKeyList, ExportType, rCountHasLob);
					if (exportCount == -1) {
						System.out.println("\n\n --- ERROR: table info " + tableName);
						rtn = false;
						break;
					} else	if (exportCount == 0) {
						System.out.println(" --- WARNING: column count 0. SKIP!!");
						continue;
					}
					System.out.print("(" + exportCount);
					if (rCountHasLob.get(i) == 0) // LOB 계열(CLOB, BLOB, LONG, RAW)을 가지고 있음. 가지고 있지 않으면 -1
						System.out.print("L");
					System.out.print(")");
					
					if (ExportType.equals("tablelist")) {
						if (rCountHasLob.get(i) == 0) { // LOB 계열(CLOB, BLOB, LONG, RAW)을 가지고 있음. 가지고 있지 않으면 -1
							rCountHasLob.set(i, getRecordCount(tableName));
						}
					}

					System.out.print(",Index/unique");
					if ((exportCount = exportIndex(tableName, bwIndex, bwFxIndex, bwPKey, bwFKey, PKeyList, FKeyList)) == -1) {
						rtn = false;
						break;
					}
					System.out.print("(" + exportCount + ")");

					PKeyList.clear();
					FKeyList.clear();
				}
				
				if (ExportType.equals("all")) {
					System.out.print(",");
				}
				if (ExportType.equals("all") || ExportType.equals("data")) {
					System.out.print("Data");
					if ((exportCount = exportData(tableName, sCharset, tCharset)) < 0) {
						rtn = false;
						break;
					}
					System.out.print("(" + exportCount + ")");
					
					totalExportedData += exportCount;
				}
				
				System.out.println(" -- done");
			}
			
			if (ExportType.equals("all") || ExportType.equals("schema")|| ExportType.equals("tablelist") ) {
				System.out.println("");
				System.out.print("Exporting View");
				if ((exportCount = exportView(bwView)) == -1) {
					System.out.println("\n\n --- ERROR: exporting view");
					rtn = false;
				} else {
					System.out.println("(" + exportCount + ") -- done");
				}

				System.out.print("Exporting Sequence");
				if ((exportCount = exportSequence(bwTable)) == -1) {
					System.out.println("\n\n --- ERROR: exporting sequence");
					rtn = false;
				} else {
					System.out.println("(" + exportCount + ") -- done");

					System.out.print("Exporting Trigger");
					if ((exportCount = exportTrigger(bwEtc)) == -1) {
						System.out.println("\n\n --- ERROR: exporting trigger");
						rtn = false;
					} else {
						System.out.println("(" + exportCount + ") -- done");

						System.out.print("Exporting StoreProcedure");
						if ((exportCount = exportStoredProcedure(bwEtc)) == -1) {
							System.out.println("\n\n --- ERROR: exporting StoreProcedure");
							rtn = false;
						} else
							System.out.println("(" + exportCount + ") -- done");
					}
				}

				if (ExportType.equals("schema") || ExportType.equals("tablelist")) 
					makeTableListFile(TableList, rCountHasLob, ExportType);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bwTable != null) bwTable.close();
				if (bwView != null) bwView.close();
				if (bwPKey != null) bwPKey.close();
				if (bwFKey != null) bwFKey.close();
				if (bwIndex != null) bwIndex.close();
				if (bwFxIndex != null) bwFxIndex.close();
				if (bwEtc != null) bwEtc.close();

				if (fwTable != null) fwTable.close();
				if (fwView != null) fwView.close();
				if (fwPKey != null) fwPKey.close();
				if (fwFKey != null) fwFKey.close();
				if (fwIndex != null) fwIndex.close();
				if (bwFxIndex != null) bwFxIndex.close();
				if (fwEtc != null) fwEtc.close();
			} catch (IOException e) {
				rtn = false;
			}
		}

		System.out.println();
		if (ExportType.equals("all") || ExportType.equals("data"))
			System.out.println("  ---> Total exported data count: " + totalExportedData);
		System.out.println("   ---> Elasped time: " + (System.currentTimeMillis() - beginTime) / 1000 + " sec");
		
		return rtn;
	}
	
	public static void main(String[] args) {
		if (args.length < 8) {
			System.out.print("Usage: mCMT [-s|-d|-S] [-b] <a|t|d> <source IP> <source Port> <source DB> <source USER> <souce PW>");
			System.out.print(" <source charset> <target charset> [-p <output dir>] [-t table name]");
			System.out.println(" [-w where clause] [Table list file name]");
			System.out.println("");
			System.out.println("       -s: SCHEMA only, make 5 table list files");
			System.out.println("       -d: DATA only");
			System.out.println("       -S: SCHEMA only, make balanced 5 table list files divided by number of records(LOB included)");
			System.out.println("       -b: without blob data, only work with -d and [Table list file]");
			System.out.println("       a: altibase, t:tibero, d:DB2");
			System.out.println("       <source Port>: 0 (default port: Altibase=20300, Tibero=8629, DB2=50000) or actual port number");
			System.out.println("       <charset>: ksc5601|euc-kr|utf8");
			System.out.println("       <output dir>: output file directory. default: current dir");
			System.out.println(" - if a blob data size >= 128M, the value is truncated under 128M and display the number of data based on column not record.");
			System.out.println(" * version: " + versionStr);
			
			System.exit(0);
		}
		
		int		idx = 0;

		String	ExportType = "all";
		String	DBtype = null;
		int		DBport = 0;
		String	DBpw = null;
		String	sCharset = null;
		String	tCharset = null;
		String	TableListFname = null;

		String	URL = null;

		Properties	sProps = new Properties();

		if (args[idx].equals("-s")) {
			ExportType = "schema";
			idx++;
		} else if (args[idx].equals("-d")) {
			ExportType = "data";
			idx++;
		} else if (args[idx].equals("-S")) {
			ExportType = "tablelist";
			idx++;
		}

		if (args[idx].equals("-b")) {
			idx++;
			if (ExportType.equals("data"))
				withoutBlob = true;
		}
		
		DBtype = args[idx++].toLowerCase();
		srcIP = args[idx++];
		DBport = Integer.parseInt(args[idx++]);
		srcDBname = args[idx++];
		
		// TIBERO, Altibase, DB2 dictionary 정보는 대문자로 되어있음.
		srcDBuser = args[idx++].toUpperCase();
		DBpw = args[idx++];

		sCharset = args[idx++];
		if (sCharset.equals("ksc5601")) {
			sCharset = "ko16ksc5601";
		} else if (!sCharset.equals("euc-kr") && !sCharset.equals("utf8")) {
				System.out.println("Invalid charset: " + sCharset);
				System.out.println("  support only: ksc5601, euc-kr, utf8");
				System.exit(0);
		}
		tCharset = args[idx++];
		if (tCharset.equals("ksc5601")) {
			tCharset = "ko16ksc5601";
		} else if (!tCharset.equals("euc-kr") && !tCharset.equals("utf8")) {
				System.out.println("Invalid charset: " + tCharset);
				System.out.println("  support only: ksc5601, euc-kr, utf8");
				System.exit(0);
		}
		
		if ((args.length > idx) && args[idx].equals("-p")) {
			idx++;
			outputDir = args[idx++]; 
		}
		if (args.length > idx) {
			TableListFname = args[idx];
		} else {
			if (withoutBlob) withoutBlob = false;
		}

		try {
			if (DBtype.equals("o")) {
				srcDB = "oracle";
				if (DBport == 0) DBport = 1521;
				Class.forName("oracle.jdbc.driver.OracleDriver");
				URL = "jdbc:oracle:thin:@" + srcIP + ":" + DBport + ":" + srcDBname;
			} else if (DBtype.equals("a")) {
				srcDB = "altibase";
				if (DBport == 0) DBport = 20300;
				Class.forName("Altibase.jdbc.driver.AltibaseDriver");
				URL = "jdbc:Altibase://" + srcIP + ":" + DBport + "/" + srcDBname;
			} else if (DBtype.equals("t")) {
				srcDB = "tibero";
				if (DBport == 0) DBport = 8629;
				Class.forName("com.tmax.tibero.jdbc.TbDriver");
				URL = "jdbc:tibero:thin:@" + srcIP + ":" + DBport + ":" + srcDBname;
			} else if (DBtype.equals("d")) {
				srcDB = "db2";
				if (DBport == 0) DBport = 50000;
				Class.forName("com.ibm.db2.jcc.DB2Driver");
				URL = "jdbc:db2://" + srcIP + ":" + DBport + "/" + srcDBname;
			} else {
				System.out.println("Source Databases should be altibase|tibero|DB2." + DBtype + ".");
				System.exit(0);
			}
		} catch(ClassNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		try {
			outputPrefix = outputDir + "/" + getPrefix(srcIP) + "_" + srcDBname + "_" + srcDBuser;
			
			fwCheckListPK = new FileWriter(outputPrefix + ".PK.checklist");
			fwCheckListDefault = new FileWriter(outputPrefix + ".DefaultValue.checklist");
			fwCheckListFloat = new FileWriter(outputPrefix + ".Float.checklist");
			fwCheckListNumber = new FileWriter(outputPrefix + ".Number.checklist");
			fwCheckListBlob = new FileWriter(outputPrefix + ".Blob.checklist");

			bwCheckListPK = new BufferedWriter(fwCheckListPK);
			bwCheckListDefault = new BufferedWriter(fwCheckListDefault);
			bwCheckListFloat = new BufferedWriter(fwCheckListFloat);
			bwCheckListNumber = new BufferedWriter(fwCheckListNumber);
			bwCheckListBlob = new BufferedWriter(fwCheckListBlob);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		}
		
		System.out.println("  --- mCMT(v." + versionStr + ") will be export from " + srcDB.toUpperCase() + "-" + getPrefix(srcIP) + "." + srcDBname + "(" + sCharset + ")." + srcDBuser + "/" + DBpw + " to CUBRID(" + tCharset + ")");
		ArrayList<String>	TableList = new ArrayList<String>();
		try {
			if (srcDB.equals("altibase")) {
				sProps.put("user", srcDBuser);
				sProps.put("password", DBpw);
				sProps.put("encoding", sCharset); // 이게 맞는듯
				sProps.put("characterencoding", sCharset); // 누근가가 이렇게 올려놓은게 있어서...
				DBconn = DriverManager.getConnection(URL, sProps);
			} else if (srcDB.equals("tibero")) {
				sProps.setProperty("user", srcDBuser);
				sProps.setProperty("password", DBpw);
				sProps.setProperty("characterset", sCharset);
				DBconn = DriverManager.getConnection(URL, sProps);		
			} else if (srcDB.equals("db2")) {
				sProps.setProperty("user", srcDBuser);
				sProps.setProperty("password", DBpw);
				sProps.setProperty("charset", sCharset);
				DBconn = DriverManager.getConnection(URL, sProps);		
			} else
				DBconn = DriverManager.getConnection(URL, srcDBuser, DBpw);
			
			if (TableListFname != null) {
				if (getTableListFromFile(TableListFname, TableList) < 0) {
					DBconn.close();
					System.exit(0);
				}
			} else {
				if (getTableList("T", TableList) < 0) {
					DBconn.close();
					System.exit(0);
				}
			}

			System.out.print("Table(s): " + TableList.size() + " ");
			System.out.println(TableList);
			if (TableList == null || TableList.isEmpty()) {
				System.out.println("NONE");
				DBconn.close();
				System.exit(0);
			}
			
			exportDB(TableList, ExportType, sCharset, tCharset);
			System.out.println();
			System.out.println(srcDB.toUpperCase() + "-" + srcDBname + "(" + srcDBuser + ") exported!!!");
			
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			TableList.clear();
			try {
				if (DBconn != null) DBconn.close();
			} catch (SQLException e) {
			}
		}

		try {
			bwCheckListPK.close();
			bwCheckListDefault.close();
			bwCheckListFloat.close();
			bwCheckListNumber.close();
			bwCheckListBlob.close();
			fwCheckListPK.close();
			fwCheckListDefault.close();
			fwCheckListFloat.close();
			fwCheckListNumber.close();
			fwCheckListBlob.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
