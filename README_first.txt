# mCMT
CUBRID migration tool for Altibase, Tibero, DB2.

* 사용법 : mCMT [-s|-d|-S] [-b] <a|t|d> <source IP> <source Port> <source DB> <source USER> <souce PW> <source charset> <target charset> [-p <output dir>] [Table list file name]
             -s: SCHEMA only, 5개의 table list 화일을 만듦. 단순 테이블 이름순.
             -d: DATA only
             -S: SCHEMA only, LOB 계열이 포함된 경우 레코드 수를 기준으로 하고, LOB 가 없는 경우는 -s와 동일.
	  -b: blob 는 null 로 내려받음. -d 옵션과 테이블 목록 화일이 있는 경우만 동작.
             a:altibase, t:tibero, d:DB2
	 <source Port>: 0 (default port: Altibase=20300, Tibero=8629, DB2=50000) 또는 실 사용 포트 번호
            <charset>: ksc5601 | euc-kr | utf8
            -p <output dir>: 스키마/데이터화일 출력 디렉토리. default: 현재 디렉토리
            <table list file name> : line 단위 table 이름.

* 호환: JAVA 1.5 이상

* 실행방법) 
   - -s 또는 -S 이용 스키마만 먼저 받은후 -d 로 데이터 export 권장
   - loaddb 중 에러 발생되는 테이블에 대하여는 table list 화일을 이용하여 해당 테이블만 따로 export 가능.
   - 데이터 양이 많거나 LOB 가 있는 경우 테이블별 table list 화일을 이용하여 여러개의 창에서 동시 수행
   - AS-IS DB 서버에서 직접수행하는 것이 제일 좋음(data file 을 저장할 공간이 충분한지 반드시 확인 필요)
   - 데이터 로딩은 같이 제공되는 load.sh 이용 (아래에 설명)

* 실행예) altibase, DB서버: localhost 접속port: 기본포트,  db명: adb, 사용자ID: dbuser, 사용자암호: dbpw, AS-IS 문자셑: ksc5601, TO-BE 문자셑: utf8
   - 스키마만 받고, AS-IS 문자셋이 ksc5601 이었고, 이를 utf8 로 변환하여 받음.
     $java -cp ./mCMT.jar;\altibase6.5\altibase.jar mCMT/mCMT -s a localhost 0 adb dbuser dbpw ksc5601 utf8
   - /output 아래 결과 화일 저정
     $java -cp ./mCMT.jar;\altibase6.5\altibase.jar mCMT/mCMT a localhost 0 adb dbuser dbpw ksc5601 utf8 -p /output 
   - table.list 에 저장된 테이블에 대하여만 데이터 받음
     $java -cp ./mCMT.jar;\altibase6.5\altibase.jar mCMT/mCMT -d a localhost 0 adb dbuser dbpw ksc5601 utf8 table.list
   - 데이터가 많아 동시에 받고자 하는 경우
     - 일단 -s 옵션을 이용하여 스키마만 받음. 
       결과를 보면 <IP뒷자리>_<DB명>_<사용자명>.table_1.list, ... <IP뒷자리>_<DB명>_<사용자명>.table_5.list 화일이 만들어져 있음. 이것을 이용하여 데이터 export 수행.
     - telnet 창을 5개를 띄운후 동시 실행. 각 창마다 테이블 목록 화일 번호만 다르게 하면 됨.
       $java -cp ./mCMT.jar;\altibase6.5\altibase.jar mCMT/mCMT -d a localhost 0 adb dbuser dbpw ksc5601 utf8 <IP뒷자리>_<DB명>_<사용자명>.table_1.list

 * 화면출력 예
     --- mCMT(v.20210531) will be export from ALTIBASE-localhost.adb(ksc5601).DBUSER/dbpw to CUBRID(utf8)
   Table(s): 11 [AAA, INFO, TBL, TTT, TBL0, TBL3, TBL1, T1, TBL2, T2, DTABLE]
      - exporting 1/11 [AAA] Columns(19L),Index/unique(1),Data:31093B-1234567890(31093) -- done
      ....
   Exporting View(3) -- done
   Exporting Sequence(4) -- done
   Exporting Trigger(1) -- done
   Exporting StoreProcedure(1) -- done

      ---> Elasped time: 1 sec

   ALTIBASE-adb(DBUSER) exported!!!

   --- mCMT(v.20210531) will be export from ALTIBASE-localhost.adb(ksc5601).DBUSER/dbpw to CUBRID(utf8)
     version: 20210531, AS-IS 알티베이스 데이터베이스 adb, 문자셑 ksc5601, 데이터베이스 계정 DBUSER, 암호 dbpw, TO-BE:CUBRID utf8 문자셑으로 추출
   Table(s): 11 [AAA, INFO, TBL, TTT, TBL0, TBL3, TBL1, T1, TBL2, T2, DTABLE]
     총 11개 테이블, 그 목록 표시
   - exporting 1/11 [AAA] Columns(19L),Index/unique(1),Data:31093B-1234567890(31093) -- done
     총 11개 테이블중 1번째 테이블 exporting
     AAA: 테이블 이름
     컬럼 19개, 인덱스/유니크 1개. 컬럼개수 뒤에 L이 표시되면 CLOB/BLOB/LONG/RAW 를 포함한 테이블.
     데이터 총 31093건. B 가 표시되면 BLOB, C 가 표시되면 CLOB 을 포함한 테이블.
     총 건수 다음 -123 은 1은 10%, 2는 20% 진행을 표시함.
     () 안이 실제 가져온 건수, 31093건 가져옴.

   - blob의 경우 최대 크기(128M미만) 초과 또는 blob 읽기 실패(insert 시 실패한것이 남아있는 경우 있음) 에 대한 정보 추가 출력
     -- done 앞 부분에 출력.
     3개 레코드가 최대 크기 초과: [BigBlob(>=128M):3]
     5개 레코드 blob 읽기 오류: -E:5

       
 * 출력화일 형식: <IP 뒤2자리|hostname>_<DB name>_<DB User name>.<구분>
   - 구분: table : table, serial
            view : 원본 DB 의 것 그대로 출력. 질의 스펙이 CUBRID 에 맞지 않거나, 실제 없는 테이블이 있을 수 있음.
            PK
            FK
            index: index, unique
            findex : function based index 정보만 따로. 함수 수정 필요할 수 있어서 별도의 화일로 저장.
            etc : trigger, stored procedure 포함. trigger와 stored procedure 는 CUBRID 와 다른 문법이 있어 이름만 표시.
            data : table 별로 만듦

            PK.checklist : PK 가 없는 테이블 목록
            DefaultValue.checklist : default value 가 있는 컬럼에 대하여 컬럼 정보와 default 값 기록. default 에 함수가 사용된 경우 특히 확인 필요.
              - TBL11(DT date) '2021-12-25' -> '2021-12-25' : TBL11 테이블의 DT 컬럼이 date 타입이며 AS-IS default 가 '2021-12-25' 인것을 -> 로 변환.
            Float.checklist : numeric 의 크기를 지정하지 않은 경우이며, 원 컬럼이 float 였을 가능성이 높음. 소수점이후 값이 있을 경우 기록
            Number.checklist : numeric 최대 38자리 이므로, 38자리 초과시 38자리로 자름. 자르기 전후 값 기록, DB2의 경우 DECFLOAT 타입이 있다는 정보 기록.
            Blob.checklist: 128M 이상되는 테이블명과 컬럼명 기록.

            table_[1-5].list

* data 화일은 blob 을 가지고 있는 경우 실 데이터의 2배 이상 크기로 저장될 수 있음.
     - 이는 화일의 ASCII 를 BYTE 문자열로 표현하는 과정에서 발생. 255 는 16진수 문자 2개로 표현되므로, 1문자는 2자리로 표현되는 것임.
     - blob 은 bit varying(1G)로 저장됨. 따라서 최대 128M-1byte 만큼만 저장됨. 이를 초과하는 경우 해당 크기를 초과하지 않는 정도로만 bit varying 으로 저장.
       blob을 가져올때 네트웤 상황에 따라 가져오는 데이터 양에 차이가 발생하여 정확히 최대 크기만큼 저장할 수 없음.
			
 * 기능 설명 (앞서 설명이 누락되는 부분 위주)
   - 예약어 사용 가능성으로 모든 테이블명/컬럼명은 [] 로 감쌈.
     - objects 화일: table명, column명 예약어 [] 로 감싸지 않아도 되나, 한글명 있을 경우 오류. 결국 [] 로 감싸야 함.
   - 컬럼이 없는 테이블은 추출하지 않음. tablelist 를 잘못줬을 경우.
   - key 이름을 default 로 한 경우 CUBRID default 를 따르도록 key 이름 제거.
   - unique 와 동일한 key 가 존재하는 경우 unique 는 건너뜀.
   - function based index 의 경우 대문자로 catalog/dictonary 에 저장되는데, 대문자 상수가 있을 수 있어 그대로 사용함.
   - number 자리수 없는 경우 numeric(38,10) 으로 생성.
     - tibero 에서 float 는 number 로 표현되며, float 인지를 dictionary 에서 구분 불가
     - float 로 추정되어 .table 화일 DDL 속에 타입을 number(38,10) 으로 하고 그 다음에 FLOAT? 로 주석 표시.
   - numeric 는 최대 38자리 이므로, 38자리 초과시 38자리로 자름
   - default 값에 대하여 date, time, datetime 일 경우 CUBRID 에 맞게 변경
     tibero: date -> datetime으로 변경되므로 sysdate 를 sysdatetime 으로.
     db2 : current date ->sysdate, current time -> systime, current timestamp -> sysdatetime 으로 변경
   - default 값이 상수값일 경우 그대로 옮김. CUBRID 에 맞지 않을 수 있음. Default.checklist 확인 필요.
   - checklist 화일을 만들어 눈으로 확인할 필요가 있는 경우 확인할 수 있도록 함. 
   - LOB 류는 다음과 같이 변환하고, DDL 에 주석 표시
      - blob --> bit varying. DDL 상에 해당 타입 앞 부분에 주석으로 bLoB 표시
                 128M 미만까지만 저장. 128M 이상은 자름.(크기를 마리알수가 없기때문)
      - clob --> string. DDL 상에 해당 타입 앞 부분에 주석으로 cLoB 표시
      - long --> string. DDL 상에 해당 타입 앞 부분에 주석으로 longLoB 표시
      - raw --> bit varying. DDL 상에 해당 타입 앞 부분에 주석으로 rawLoB 표시
   - view 에 대하여 DB2는 function based index 를 view 로 만듦. <index_name>_V 형태로 만듦. 
     - index 생성시 주석으로 FUNC_INDEX 을 넣어주었으니, view 이름에 _V 가 있는 경우 index 에 있는지 확인해 볼 필요 있음.
   - serial 이 nocycle 이고 최대값에 도달하여 에러가 발생하는 경우 설정된 max 값으로 current_value 를 설정 함.
   - serial 에 대하여 current value 를 대부분 얻지 못함. 따라서 next value 를 이용해 현재 값으로 설정.
       
 * table 단위로 data 화일 만들며, 디스크 여유 공간이 10% 미만일 경우 메세지 출력. 공간 정리후 아무키나 입력하면 계속 진행 (기능테스트는 되었으나 믿고 사용할 것을 권장하고 싶지 않음)
   - object file 이 너무 커지는 문제와, loaddb 에러발생시 해당 테이블만 다시 내려받아 재시도 할 수 있도록 하기 위함.
   - 여유공간은 총간간대비 10% (소스상 설정가능) 미만일 경우
   - 1개의 테이블 추출전, LOB 가 포함된 1개의 레코드 추출후 여유공간 확인
   - 화일명: <DBname>_<DBuser>_<table명>___99.data 처럼 추가적으로 만들어지며, 99은 전체 레코드대비 현 data 화일에 기록되는 첫 레코드의 rownumber
         
 * 기타 참고 사항
   - oracle 기본키 not null 설정없어도 만들어지지만 dictionary 에는 nullable 'N'
   - oracle timestamp .xx 초가 있다. 이는 CUBRID 에서는 버림.
   - oracle number 로만 설정시 최대 자리수는 실제 40(소수점앞2, 소수점 38까지 허용). 자리수 지정해도 소수점자리는 우리가 ORACLE 보다 하나 적다.

 * load 수행시 유의사항
   - 별도의 load.sh 사용.
     - 테이블 단위 objects 화일 loaddb 수행기능.
       - 성공시 OK.mmdd 로 화일 옮겨주며, 실패시 중단하고 화일명 보여주므로 해당 화일 확인하고 처리하면 됨.
       - 실패시 에러 화일 확인에 시간이 소요될 수 있으므로 ERR 디렉토리 만들어 해당 화일 옮기고 나머지 load 처리후 에러는 별도 확인하는 것이 유리함.
   - load순서: table, data, PK, FK, index, findex, view(내용 확인 필요)
   - schema 는 csql 에서 수행하여 이상 여부 확인. --no-auto-commit 권장
     - DefaultValue.checklist 확인: 함수 사용시 ' 사용 부분. 함수 이름 지원 여부 확인.
     - Float.checklist 확인: numeric(38,10) 으로 처리 가능한지 확인. 소수점이하 자리수가 다를 수 있으므로 확인하여 스키마 손으로 조정하고 load 하여야 함.
   - loaddb 수행시 --no-log, -l 사용하지 말것. 일부 데이터 입력중 에러 발생 가능성 있음.
     - 데이터 입력시 오류발생하면 csql 로 들어가 입력된 데이터만 삭제. 해당 테이블만 다시 받아 처리하면 됨.      
 
* load 후 이관 개수 확인을 위한 getTableInfo
  - 사용법: getTableInfo <hostname/IP> <DB name> <DB user> <DB pw>
     $java -cp ./getTableInfo.jar;$CUBRID/jdbc/cubrid_jdbc.jar getTableInfo/getTableInfo localhost adb dbuser dbpw
        - TB1: 10
        - TB10: 21

       adb Table count:: 2
        - Total record count: 21
   
