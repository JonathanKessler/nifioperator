NEXT_WAIT_TIME=0
until asdsda || [ $NEXT_WAIT_TIME -eq 10 ]; do
   echo "Command failed, sleeping $NEXT_WAIT_TIME seconds"
   sleep $(( NEXT_WAIT_TIME++ ))
done
