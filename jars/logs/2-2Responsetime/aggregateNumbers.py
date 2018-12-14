resultLine = "Average response time:"
#resultLine = "Total time [s], Throughput [ops/s]:"
totalTimes = []
throughputTimes = []
for i in range(16):
	outfile = open("out%d.txt" % i)
	for line in outfile:
		if (resultLine in line):
			resultNumbers = line.split(resultLine)[1]
			print(float(resultNumbers))
			#totalTime = float(resultNumbers.split(",")[0])
			#throughputTime = float(resultNumbers.split(",")[1])
			#print(totalTime)
			#print(throughputTime)
