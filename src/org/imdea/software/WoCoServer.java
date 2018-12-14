package org.imdea.software;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.sql.SQLOutput;
import java.util.*;
import java.util.concurrent.*;
import java.util.Collections;

public class WoCoServer {
    private static long timeLastPrint;
    private static long timeCreate;
	public static final char SEPARATOR = '$';
	private HashMap<Integer, StringBuilder> buffer;
	public HashMap<Integer, HashMap<String, Integer>> results;
	private static HashMap<Integer, ArrayList<Float>> receiveTimes = new HashMap<>();
	private static HashMap<Integer, ArrayList<Float>> cleanTimes= new HashMap<>();
	private static HashMap<Integer, ArrayList<Float>> serializingTimes= new HashMap<>();
	private static HashMap<Integer, ArrayList<Float>> wordCountTimes= new HashMap<>();
	private static ArrayList<Integer> clientList = new ArrayList<>();
    //private static ExecutorService pool;
    HashMap<Integer, Integer> clientIdHash = new HashMap<>();
    List<MyThread> clientThreadMap;
    private int threadNumber;
    private int currentThreadsJob = 0;

	/**
	 * Constructor of the server.
	 */
	public WoCoServer(int threadNumber) {
		buffer = new HashMap<>();
		results = new HashMap<>();
		this.threadNumber = threadNumber;
		//pool = Executors.newFixedThreadPool(threadNumber);
		clientThreadMap = new ArrayList<>(threadNumber);
        for (int i = 0; i < threadNumber; i++) {
            clientThreadMap.add(new MyThread());
            clientThreadMap.get(i).start();
        }
	}
	
	/**
	 * This function handles data received from a specific client (TCP connection).
	 * Internally it will check if the buffer associated with the client has a full
	 * document in it (based on the SEPARATOR). If yes, it will process the document and
	 * return true, otherwise it will add the data to the buffer and return false
	 * @param clientId
	 * @param dataChunk
	 * @return A document has been processed or not.
	 */
	public boolean receiveData(int clientId, String dataChunk, boolean cleanMode, SocketChannel client, long beforeReceiveTime) {
		StringBuilder sb;
        if (!results.containsKey(clientId))
			results.put(clientId, new HashMap<String, Integer>());
		if (!buffer.containsKey(clientId)) {
			sb = new StringBuilder();
			buffer.put(clientId, sb);
		} else
			sb = buffer.get(clientId);
		sb.append(dataChunk);
		if (dataChunk.indexOf(WoCoServer.SEPARATOR)>-1) {                       			//we have at least one line
			String bufData = sb.toString();
			int indexNL = bufData.indexOf(WoCoServer.SEPARATOR);
			String line = bufData.substring(0, indexNL);
			String rest = (bufData.length()>indexNL+1) ? bufData.substring(indexNL+1) : null;
			if (indexNL==0)
				System.out.println("SEP@"+indexNL+" bufdata:\n"+bufData);
			if (rest != null) {
				System.out.println("more than one line: \n"+rest);
				try {
					System.in.read();
				} catch (IOException e) { e.printStackTrace(); }
				buffer.put(clientId, new StringBuilder(rest));
			} else
				buffer.put(clientId, new StringBuilder());
			HashMap<String, Integer> wc = results.get(clientId);
            WordCounter wordCounter  = new WordCounter(line, wc, cleanMode, clientId, this, results, client, beforeReceiveTime);
            if (!clientIdHash.containsKey(clientId)) {          //basically round robin implementation
                clientIdHash.put(clientId, currentThreadsJob);
                if (currentThreadsJob + 1 <= threadNumber - 1)
                    currentThreadsJob ++;
                else
                    currentThreadsJob = 0;
                //System.out.println("clientId:" + clientId + " to thread:" + currentThreadsJob);
                clientThreadMap.get(currentThreadsJob).threadQueue.add(wordCounter);                              //assign client Job to a thread based on his ClientId
            } else {
                //System.out.println("clientId:" + clientId + " to thread: " + clientIdHash.get(clientId));
                clientThreadMap.get(clientIdHash.get(clientId)).threadQueue.add(wordCounter);
            }
            //long beforeWordCount = System.nanoTime();                                 //older approach with threadpool
            //wc = clientThreadMap.get(clientId % threadNumber).getResult();
            /*Future<HashMap<String, Integer>> wordCount = pool.submit(() -> doWordCount(line, wc, cleanMode, clientId));
            try {
                HashMap<String, Integer> result = wordCount.get();
                results.put(clientId, result);
            } catch (Exception e) { e.printStackTrace();}*/
            //wordCountTimes.get(clientId).add((float)((System.nanoTime() - beforeWordCount)/1000000000.0));
			return true;
		} else
			return false;
	}

    /**
     * Initializes the data structure that holds statistical information on requests.
     */
    private static void initStats() {
        timeLastPrint = System.nanoTime();
        timeCreate = timeLastPrint;
    }

    private static void printStats() {
		float receiveTimeSum = 0, serializingTimeSum = 0, wordCountTimeSum = 0, cleanTimeSum = 0;
		int receiveTimeEvents = 0, serializingTimeEvents = 0, wordCountTimeEvents = 0, cleanTimeEvents = 0;
		boolean withPercentiles = true;
		ArrayList<Float> totalCleaningTimes = new ArrayList<>();
        ArrayList<Float> totalWordCountTimes = new ArrayList<>();
        ArrayList<Float> totalSerializingTimes = new ArrayList<>();
        ArrayList<Float> totalReceiveTimes = new ArrayList<>();
		for (int i = 0; i < clientList.size(); i++) {
			int clientId = clientList.get(i);
			receiveTimeEvents += receiveTimes.get(clientId).size();
			serializingTimeEvents += serializingTimes.get(clientId).size();
			wordCountTimeEvents += wordCountTimes.get(clientId).size();
			cleanTimeEvents += cleanTimes.get(clientId).size();
			for (int j = 0; j < receiveTimes.get(clientId).size(); j++) {
			    Float currentReceiveValue = receiveTimes.get(clientId).get(j);
                receiveTimeSum += currentReceiveValue;
                totalReceiveTimes.add(currentReceiveValue);
			}
			for (int j = 0; j < serializingTimes.get(clientId).size(); j++) {
                Float currentSerializeValue = serializingTimes.get(clientId).get(j);
                serializingTimeSum += currentSerializeValue;
                totalSerializingTimes.add(currentSerializeValue);
			}
			for (int j = 0; j < cleanTimes.get(clientId).size(); j++) {
                Float currentCleanValue = cleanTimes.get(clientId).get(j);
                cleanTimeSum += currentCleanValue;
                totalCleaningTimes.add(currentCleanValue);
            }
			for (int j = 0; j < wordCountTimes.get(clientId).size(); j++) {
                Float currentWordCountValue = wordCountTimes.get(clientId).get(j);
                wordCountTimeSum += currentWordCountValue;
                totalWordCountTimes.add(currentWordCountValue);
            }
		}
        if (withPercentiles) {
            Collections.sort(totalCleaningTimes);
            Collections.sort(totalReceiveTimes);
            Collections.sort(totalSerializingTimes);
            Collections.sort(totalWordCountTimes);
            System.out.println("-----");
            System.out.print("Response time percentiles [ms]: ");
            System.out.print("\n");
            System.out.println("Clean size:" + totalCleaningTimes.size());
            for (int p=1; p<=100; p++) {
                //System.out.println("Index: " + (((long) totalCleaningTimes.size())*(long)p/(long)100-(long)1));
                System.out.println(p+" cleaning:"+totalCleaningTimes.get((int)((long)totalCleaningTimes.size()*(long)p/(long)100-(long)1)));        //the multiplication surpasses int_max
                System.out.println(p+" serializing:"+totalSerializingTimes.get((int)((long)totalSerializingTimes.size()*(long)p/(long)100-(long)1)));
                System.out.println(p+" receiving:"+totalReceiveTimes.get((int)((long)totalReceiveTimes.size()*(long)p/(long)100-(long)1)));
                System.out.println(p+" wordCount:"+totalWordCountTimes.get((int)((long)totalWordCountTimes.size()*(long)p/(long)100-(long)1)));
            }
        }
		System.out.println("Receiving times: " + receiveTimeSum + " with average:" + receiveTimeSum/receiveTimeEvents + "\nSerializing sum: " + serializingTimeSum
				+ " with average:" + serializingTimeSum/serializingTimeEvents + "\nWordCount sum: " + wordCountTimeSum +
				" with average:" + wordCountTimeSum/wordCountTimeEvents + "\nCleantime Sum: " + cleanTimeSum + " with average:" + cleanTimeSum/cleanTimeEvents);
    }

    /**
     * Initializes data structures that hold statistical information on processing times for each client
     * @param clientId Id of client for which to initialize the arrays for storing the times
     */
	private static void initClientStats(int clientId) {
    	ArrayList<Float> receiveTimesArray = new ArrayList<>();
		ArrayList<Float> wordCountTimesArray = new ArrayList<Float>();
		ArrayList<Float> serializingTimesArray = new ArrayList<Float>();
		ArrayList<Float> cleaningTimesArray = new ArrayList<Float>();
		receiveTimes.put(clientId, receiveTimesArray);
		cleanTimes.put(clientId, cleaningTimesArray);
		serializingTimes.put(clientId, serializingTimesArray);
		wordCountTimes.put(clientId, wordCountTimesArray);
	}
    public static void main(String[] args) throws IOException {
		/*if (args.length!=4) {
			System.out.println("Usage: <listenaddress> <listenport> <cleaning> <threadcount>");
			System.exit(0);
		}*/
		String lAddr = args[0];
        initStats();
		int lPort = Integer.parseInt(args[1]);
		boolean cleanMode = Boolean.parseBoolean(args[2]);
		int threadCount = Integer.parseInt(args[3]);
		if (threadCount < 1) {
            System.out.println("Threads must be at least 1");
            System.exit(0);
        }

        WoCoServer server = new WoCoServer(threadCount);

        Selector selector = Selector.open();
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        InetSocketAddress myAddr = new InetSocketAddress(lAddr, lPort);
        serverSocket.bind(myAddr);
        serverSocket.configureBlocking(false);

        int ops = serverSocket.validOps();
        SelectionKey selectKey = serverSocket.register(selector, ops, null);         // Infinite loop.. Keep server running
        ByteBuffer bb = ByteBuffer.allocate(1024*1024);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("shutting down");
                printStats();
            }
        });
        while (true) {
            selector.select();
            Set<SelectionKey> readyKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = readyKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    SocketChannel client = serverSocket.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("Connection Accepted: " + client.getLocalAddress() + "\n");
                } else if (key.isReadable()) {
                    SocketChannel client = (SocketChannel) key.channel();
                    int clientId = client.hashCode();
                    clientList.add(clientId);
                    if (receiveTimes.get(clientId) == null)
                        initClientStats(clientId);
                    bb.rewind();
                    int readCnt = client.read(bb);
                    if (readCnt>0) {
                        String result = new String(bb.array(),0, readCnt);
                        long beforeReceiveTime = System.nanoTime();
                        server.receiveData(clientId, result, cleanMode, client, beforeReceiveTime);
                    } else {
                        key.cancel();
                        /*try {
                            Thread.sleep(3000);
                            System.out.println("no more clients");
                            printStats();
                            System.exit(0);
                        }catch (InterruptedException e){ e.printStackTrace();}*/
                    }
                }
                iterator.remove();
            }
        }

    }
    static class WordCounter {
        String line;
        HashMap<String, Integer> wc;
        boolean cleanMode;
        Integer clientId;
        WoCoServer server;
        HashMap<Integer, HashMap<String, Integer>> results;
        SocketChannel client;
        long beforeReceiveTime;

        public WordCounter(String line, HashMap<String, Integer> wc, boolean cleanMode, Integer clientId, WoCoServer server,
                           HashMap<Integer, HashMap<String, Integer>> results, SocketChannel client, long beforeReceiveTime) {
            this.clientId = clientId;
            this.cleanMode = cleanMode;
            this.wc = wc;
            this.line = line;
            this.server = server;
            this.results = results;
            this.client = client;
            this.beforeReceiveTime = beforeReceiveTime;

        }
        public String serializeResultForClient(){
            if (this.results.containsKey(clientId)) {
                StringBuilder sb = new StringBuilder();
                HashMap<String, Integer> hm = this.results.get(clientId);
                for (String key : hm.keySet()) {
                    sb.append(key+",");
                    sb.append(hm.get(key)+",");
                }
                this.results.remove(clientId);
                sb.append("\n");
                return sb.substring(0);
            } else
                return "";
        }

        public void sendResultsToClient(HashMap<String, Integer> wc){
            long beforeSerializing = System.nanoTime();
            ByteBuffer ba;
            ba = ByteBuffer.wrap(serializeResultForClient().getBytes());
            try {
                client.write(ba);
            } catch (Exception e) {e.printStackTrace();}
            long afterSerializing = System.nanoTime();
            this.server.serializingTimes.get(clientId).add((float)((afterSerializing - beforeSerializing)/1000000.0));
        }
        public void countWords() {
                long beforeWordCount = System.nanoTime();
                String ucLine = line.toLowerCase();
                StringBuilder asciiLine = new StringBuilder();
                StringBuilder bufferLine = new StringBuilder();
                boolean skipTag = false;
                char lastAdded = ' ';
                long beforeCleanTime = System.nanoTime();
                //System.out.println("Thread: " + Thread.currentThread().getId() + " clientId: " + clientId);
                for (int i=0; i<line.length(); i++) {
                        char cc = ucLine.charAt(i);
                        if (cleanMode) {
                            if (cc == '<') {                             //if tag is discovered, add store all from buffer to asciiLines and start skipping the tag
                                asciiLine.append(bufferLine);
                                skipTag = true;
                            } else if (cc == '>') {                            //if tag is being closed, delete all what was in buffer.
                                skipTag = false;
                                bufferLine.delete(0, bufferLine.length());          //this is extra safety measure in case doc starts with a tag but no opening mark '<'
                                if (lastAdded != ' ') {
                                    bufferLine.append(' ');
                                    lastAdded = ' ';
                                }
                            }
                            if (((cc >= 'a' && cc <= 'z') || (cc == ' ' && lastAdded != ' ')) && !skipTag) {
                                bufferLine.append(cc);
                                lastAdded = cc;
                            }
                        } else {
                            if ((cc >= 'a' && cc <= 'z') || (cc == ' ' && lastAdded != ' ')) {
                                asciiLine.append(cc);
                                lastAdded = cc;
                            }
                        }
                    }
                    if (cleanMode)
                        this.server.cleanTimes.get(clientId).add((float)((System.nanoTime() - beforeCleanTime)/1000000000.0));
                    String[] words = asciiLine.toString().split(" ");
                    for (String s : words)                                      //I like compact code so I deleted non-required brackets around fors and ifs
                        if (wc.containsKey(s))
                            wc.put(s, wc.get(s)+1);
                         else
                            wc.put(s, 1);
                    this.results.put(clientId, wc);
                    sendResultsToClient(wc);
                    this.server.wordCountTimes.get(clientId).add((float)((System.nanoTime() - beforeWordCount)/1000000000.0));
                    long afterReceiveTime = System.nanoTime();
                    this.server.receiveTimes.get(clientId).add((float)((afterReceiveTime - beforeReceiveTime)/1000000.0));
        }
    }
    static class MyThread extends Thread {
        LinkedBlockingQueue<WordCounter> threadQueue;
        public MyThread(){
            this.threadQueue = new LinkedBlockingQueue<>();
        }
        public void run() {
            try {
                WordCounter wordCounter;
                while (true) {                         //with the BlockingQueue and take() function, the cores won't be on 100% load
                    wordCounter = threadQueue.take();
                    wordCounter.countWords();
                }
            } catch(Exception e){ e.printStackTrace(); }
        }
    }
}

