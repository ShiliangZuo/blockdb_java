package iiis.systems.os.blockdb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.regex.*;
import java.io.*;
import com.google.protobuf.util.JsonFormat;
import org.json.simple.parser.*;
import org.json.simple.*;
import org.json.JSONObject;

public class DatabaseEngine {

    private static DatabaseEngine instance = null;

    public static DatabaseEngine getInstance() {
        return instance;
    }

    public static void setup(String dataDir) {
        instance = new DatabaseEngine(dataDir);
    }

    private HashMap<String, Integer> balances = new HashMap<>();
    private long logLength = 0;
    private String dataDir;

    private HashSet<String> records = new HashSet<>();
    private LinkedList<Transaction> transactionList = new LinkedList<>();

    private int blockId = 0;
    final int N = 2/*50*/;

    final int userIdLength = 8;
    final String template = "[a-z0-9|-]{" + userIdLength + "}";
    Pattern pattern = Pattern.compile(template, Pattern.CASE_INSENSITIVE);

    Block.Builder blockBuilder = Block.newBuilder().setBlockID(blockId);
    String serverLogInfoPath;
    org.json.simple.JSONObject serverInfoJson;
    private String tmpJsonPath;

    private Semaphore semaphore = new Semaphore(1);

    //private String nonce, recentHash;
    private Boolean mined = false;
    private TreeNode<Block> recentNode = new TreeNode(blockBuilder.build());
    //private HashMap<String, TreeNode<Block>> blockHashMap = new HashMap<>();

    DatabaseEngine(String dataDir) {
        this.dataDir = dataDir;
        serverLogInfoPath = dataDir + "serverLogInfo.json";
        tmpJsonPath = dataDir + "tmp.json";

        // create/open a JSON file, stores information including:
        // 1. boolean flag : indicates whether first run
        // 2. int completedBlockNumber : indicates how many blocks we have, updated consistently as id increases
        // 3. int transientLogEntries : indicates how many entries there are in the transient log

        JSONParser parser = new JSONParser();
        File dir = new File(dataDir);
        boolean successful = dir.mkdir();
        if (successful) {
            // creating the directory succeeded
            System.out.println("directory was created successfully");
        }
        else {
            // creating the directory failed
            System.out.println("failed trying to create the directory");
        }

        try {
            Object obj = parser.parse(new FileReader(serverLogInfoPath));

            serverInfoJson = (org.json.simple.JSONObject) obj;
            System.out.println("serverInfoJson");
            System.out.println(serverInfoJson);

            boolean cleanStart = (boolean) serverInfoJson.get("cleanStart");

            if (cleanStart == false) {
                System.out.println("This is not a clean start...");
                System.out.println("Trying to restore...!!");
                this.blockId = (long) serverInfoJson.get("completedBlockNumber");
                ++this.blockId;
                this.logLength = (long) serverInfoJson.get("transientLogEntries");

                try {
                    String jsonPath = tmpJsonPath;
                    FileReader fileReader = new FileReader(jsonPath);
                    JsonFormat.parser().merge(fileReader, blockBuilder);
                } catch (FileNotFoundException e) {
                    System.out.println("Cannot find transient log, it has not been created yet");
                }

                runRestore();
            }

            /*String name = (String) jsonObject.get("name");
            System.out.println(name);
            long age = (Long) jsonObject.get("age");
            System.out.println(age);*/

            // loop array
            /*JSONArray msg = (JSONArray) jsonObject.get("messages");
            Iterator<String> iterator = msg.iterator();
            while (iterator.hasNext()) {
                System.out.println(iterator.next());
            }*/

        } catch (FileNotFoundException e) {
            //If the file is not found, then it *probably* means this is a cold start
            //e.printStackTrace();
            serverInfoJson = new org.json.simple.JSONObject();
            serverInfoJson.put("cleanStart", false);
            serverInfoJson.put("completedBlockNumber", 0);
            serverInfoJson.put("transientLogEntries", 0);

            try (FileWriter file = new FileWriter(serverLogInfoPath)) {

                file.write(serverInfoJson.toString());
                file.flush();
                System.out.println("Created serverLogInfoPath...!!");
                System.out.println(serverInfoJson);

            } catch (IOException exp) {
                exp.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

    }

    private int getOrZero(String userId) {
        /*if (balances.containsKey(userId)) {
            return balances.get(userId);
        } else {
            balances.put(userId, 1000);
        }*/
        if (balances.containsKey(userId) == false) {
            balances.put(userId, 1000);
        }
        return balances.get(userId);
    }

    public int get(String userId) {
        //logLength++;
        try {
            semaphore.acquire();
            int value = getOrZero(userId);
            semaphore.release();
            return value;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public boolean transfer(Transaction request) {
        Transaction.Types type = request.getType();
        String fromId = request.getFromID();
        String toId = request.getToID();
        int value = request.getValue();
        int fee = request.getMiningFee();
        String UUID = request.getUUID();
        try {
            semaphore.acquire();
            if (type == Transaction.Types.TRANSFER && pattern.matcher(fromId).matches()
                    && pattern.matcher(toId).matches() && !fromId.equals(toId) && value >= 0
                    && records.contains(request.getUUID()) == false) {
                int fromBalance = getOrZero(fromId);
                if (value <= fromBalance && value >= fee) {
                    writeTransactionLog(Transaction.newBuilder().
                            setType(Transaction.Types.TRANSFER).setFromID(fromId).
                            setToID(toId).setValue(value).setMiningFee(fee).build());
                    int toBalance = getOrZero(toId);
                    balances.put(fromId, fromBalance - value);
                    balances.put(toId, toBalance + value - fee);
                    records.add(UUID);
                    semaphore.release();
                    return true;
                }
            }
            semaphore.release();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void receive(Transaction request) {
        transfer(request);
    }

    public int getLogLength() {
        return (int)logLength;
    }

    public void writeTransactionLog(Transaction transaction) {
        if (logLength == 0)
            blockBuilder.setBlockID((int)(blockId)).setPrevHash("00000000").clearTransactions().setNonce("00000000");
        blockBuilder.addTransactions(transaction);
        //writeFile(dataDir + blockId + ".json", blockBuilder.build());
        writeFile(tmpJsonPath, blockBuilder.build());
        logLength++;
        logLength%=N;

        if (logLength == 0) {
            File file = new File(tmpJsonPath);
            File newFile = new File(dataDir + blockId + ".json");
            if (file.renameTo(newFile)) {
                System.out.println("File rename success, " + blockId + ".json created");
            } else {
                System.out.println("File rename failed");
                System.out.println(file.getAbsolutePath());
                System.out.println(newFile.getAbsolutePath());
            }
            ++blockId;
        }

        serverInfoJson.put("completedBlockNumber", blockId - 1);
        serverInfoJson.put("transientLogEntries", logLength);
        try (FileWriter file = new FileWriter(serverLogInfoPath))
        {
            file.write(serverInfoJson.toString());
            System.out.println("Successfully updated json object to file...!!");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private boolean runRestore() {
        try {
            semaphore.acquire();

            JSONObject serverLogJson = Util.readJsonFile(serverLogInfoPath);
            int completeBlocks = serverLogJson.getInt("completedBlockNumber");
            int transientLogEntries = serverLogJson.getInt("transientLogEntries");

            //restore the completed json blocks
            for (int i = 1; i <= completeBlocks; ++i) {
                String jsonPath = dataDir + i + ".json";
                //JSONObject jsonblock = Util.readJsonFile(jsonPath);
                FileReader fileReader = new FileReader(jsonPath);
                Block.Builder builder = Block.newBuilder();
                JsonFormat.parser().merge(fileReader, builder);

                for (int j = 0 ; j < N; ++j) {
                    System.out.println("restoring block: " + i + " transaction: " + j);
                    Transaction thisTransaction = builder.getTransactions(j);
                    restoreThisTransaction(thisTransaction);
                }
            }

            //restore transient logs
            if (transientLogEntries > 0) {
                String jsonPath = tmpJsonPath;
                FileReader fileReader = new FileReader(jsonPath);
                Block.Builder builder = Block.newBuilder();
                JsonFormat.parser().merge(fileReader, builder);
                for (int i = 0; i < transientLogEntries; ++i) {
                    System.out.println("restoring transient log: " + i);
                    Transaction thisTransaction = builder.getTransactions(i);
                    restoreThisTransaction(thisTransaction);
                }
            }

            semaphore.release();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean restoreThisTransaction(Transaction thisTransaction) {
        //String userId = thisTransaction.getUserID();
        String fromId = thisTransaction.getFromID();
        String toId = thisTransaction.getToID();
        int value = thisTransaction.getValue();
        Transaction.Types type = thisTransaction.getType();
        int fee = thisTransaction.getMiningFee();

        switch (type) {
            /*
            case PUT:
                balances.put(userId, value);
                break;
            case DEPOSIT:
                balance = getOrZero(userId);
                balances.put(userId, balance + value);
                break;
            case WITHDRAW:
                balance = getOrZero(userId);
                if (value <= balance) {
                    balances.put(userId, balance - value);
                }
                break;
            */
            case TRANSFER:
                int fromBalance = getOrZero(fromId);
                if (value <= fromBalance) {
                    int toBalance = getOrZero(toId);
                    balances.put(fromId, fromBalance - value);
                    balances.put(toId, toBalance + value - fee);
                }
                break;
            default:
                return false;
        }
        return false;
    }

    public static void writeFile(String filePath, Block block) {
        try
        {
            FileWriter fileWriter = new FileWriter(filePath);
            JsonFormat.printer().appendTo(block, fileWriter);
            fileWriter.close();
        }
        catch (IOException e)
        {
            System.out.println(e);
        }
    }

    private void produceBlock() {
        if(mined && !transactionList.isEmpty())
        {
            blockBuilder.setBlockID(++blockId).setPrevHash(Hash.getHashString(recentNode.data.getNonce())/*recentHash*/).clearTransactions().setNonce(nonce);
            Transaction transaction;
            while((transaction = transactionList.poll())!=null)
                blockBuilder.addTransactions(transaction);
            recentNode = recentNode.addChild(blockBuilder.build());
            mined = false;
        }
    }
}
