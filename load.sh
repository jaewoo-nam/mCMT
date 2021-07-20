#!/bin/bash
# load.sh

DB_NAME=risdb
DB_USER=RMS01USER
DB_PW='RMS01USER'

F_LIST=`ls -1 *.data`

#DT=`date +%m%d%H%M`
DT=`date +%m%d`
mkdir OK.$DT

for F_NAME in ${F_LIST[@]}
do
        echo "   -----> DB:$DB_NAME USER:$DB_USER PW:$DB_PW file: $F_NAME"
        cubrid loaddb -u $DB_USER -p $DB_PW -v -l -c 10000 -d $F_NAME $DB_NAME
        if [ $? -gt 0 ]; then
                echo "        ---> FAIL - $F_NAME"
                echo "   If you want to load continuously, move this $F_NAME to other directory like ERR, and run again."
                exit
        else
                echo "        > success"
                mv $F_NAME OK.$DT
        fi
done

echo "All successfully loaded data files are moved to $DT"
