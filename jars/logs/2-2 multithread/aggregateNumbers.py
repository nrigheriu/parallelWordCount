#timeToPrint = input("Do you want to print throughputTime or totalTime? (press 0 or 1)")
resultLine = "Total time [s], Throughput [ops/s]:"
totalTimes = []
throughputTimes = []
for i in range(16):
	outfile = open("out%d.txt" % i)
	for line in outfile:
		if (resultLine in line):
			resultNumbers = line.split(resultLine)[1]
			totalTime = float(resultNumbers.split(",")[0])
			throughputTime = float(resultNumbers.split(",")[1])
			#if (timeToPrint):
			#print(totalTime)
			#else:
			print(throughputTime)
			