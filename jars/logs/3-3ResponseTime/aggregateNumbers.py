resultLine = "Total time [s], Throughput [ops/s]:"
#resultLine = "Total time [s], Throughput [ops/s]:"
totalTimes = []
sizes = [1, 24, 52, 80, 103, 120, 180, 256]
throughputTimes = []
for i in sizes:
	outfile = open("out-%d.txt" % i)
	for line in outfile:
		if (resultLine in line):
			resultNumbers = line.split(resultLine)[1]
			#print(resultNumbers[1])
			print(float(resultNumbers.split(',')[1]))
			#totalTime = float(resultNumbers.split(",")[0])
			#throughputTime = float(resultNumbers.split(",")[1])
			#print(totalTime)
			#print(throughputTime)
