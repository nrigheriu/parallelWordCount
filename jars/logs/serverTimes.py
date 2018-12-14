#timeToPrint = input("Do you want to print throughputTime or totalTime? (press 0 or 1)")
averageLine = "Serializing sum:"
sizes = [80, 103, 120, 180, 256]
for i in sizes:
	outfile = open("server3Out%d.txt" % i)
	for line in outfile:
		if (averageLine in line):
			timeLine = line.split(averageLine)[1]
			timeLine = timeLine.split("with average:")[1]
			print(timeLine)