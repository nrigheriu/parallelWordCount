#!/bin/bash
for i in `seq 0 15`;
do
	echo "Clients: $i"
	echo "Clients: $i" >> logs/out$i.txt
	java -jar WoCoClient.jar localhost 3000 4 10 >> logs/out$i.txt &
        for j in `seq 1 $i`;
	do 
		java -jar WoCoClient.jar localhost 3000 4 10 &
	done
	wait
		
done   

