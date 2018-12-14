#!/bin/bash
array=(103)    # ( 1 52 103 154 205 256 )
for i in "${array[@]}"
do
	echo "Size: $i"
	echo "Size: $i" >> logs/outSlide.txt
	java -jar WoCoClient.jar localhost 3000 $i 10 >> logs/outSlide$i.txt &
        for j in `seq 1 7`;
	do 
		java -jar WoCoClient.jar localhost 3000 $i 1 &
	done
	wait
		
done   

