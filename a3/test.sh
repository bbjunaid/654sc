if [ $1 -eq 0 ]; then
    ./router 1 ubuntu1204-006 9000 6001 &
    sleep 0.1
    ./router 2 ubuntu1204-006 9000 6002 &
    sleep 0.1
    ./router 3 ubuntu1204-006 9000 6003 &
    sleep 0.1
    ./router 4 ubuntu1204-006 9000 6004 &
    sleep 0.1
    ./router 5 ubuntu1204-006 9000 6005 &
    sleep 0.1
else
    killall router
    killall java
fi
