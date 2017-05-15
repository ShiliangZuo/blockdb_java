package iiis.systems.os.blockdb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class DatabaseEngine {
    private static DatabaseEngine instance = null;

    public static DatabaseEngine getInstance() {
        return instance;
    }

    public static void setup(String dataDir) {
        instance = new DatabaseEngine(dataDir);
    }

    private HashMap<String, Integer> balances = new HashMap<>();
    private int logLength = 0;
    private int id = 1;
    private String dataDir;

    private String printWriterLogPath;
    //private String bufferedWriterLogPath;

    PrintWriter writer = null;
    //BufferedWriter bw = null;
    //FileWriter fw = null;

    DatabaseEngine(String dataDir) {
        this.dataDir = dataDir;
        this.printWriterLogPath = dataDir + "printWriterLog.txt";
        //this.bufferedWriterLogPath = dataDir + "bufferedWriterLog.txt";

        try{
            writer = new PrintWriter(printWriterLogPath, "UTF-8");
            writer.println("Transient Log:");
            writer.flush();
            System.out.println("Create and open file successful");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getOrZero(String userId) {
        if (balances.containsKey(userId)) {
            return balances.get(userId);
        } else {
            return 0;
        }
    }

    public int get(String userId) {
        //logLength++;
        return getOrZero(userId);
    }

    public boolean put(String userId, int value) {
        logLength++;
        writer.println("put: " + userId + " " + value);
        writer.flush();

        if (logLength == 50) {
            packToJSON();
        }

        System.out.println("logLength" + logLength);
        balances.put(userId, value);
        return true;
    }

    public boolean deposit(String userId, int value) {
        logLength++;
        writer.println("deposit: " + userId + " " + value);
        writer.flush();

        if (logLength == 50) {
            packToJSON();
        }

        int balance = getOrZero(userId);
        balances.put(userId, balance + value);
        return true;
    }

    public boolean withdraw(String userId, int value) {
        logLength++;
        writer.println("withdraw: " + userId + " " + value);
        writer.flush();

        if (logLength == 50) {
            packToJSON();
        }

        int balance = getOrZero(userId);
        if (value > balance) {
            return false;
        }
        balances.put(userId, balance - value);
        return true;
    }

    public boolean transfer(String fromId, String toId, int value) {
        logLength++;
        writer.println("transfer: " + fromId + " " + toId + " " + value);
        writer.flush();

        if (logLength == 50) {
            packToJSON();
        }

        int fromBalance = getOrZero(fromId);
        int toBalance = getOrZero(toId);
        if (value > fromBalance) {
            return false;
        }
        balances.put(fromId, fromBalance - value);
        balances.put(toId, toBalance + value);
        return true;
    }

    public int getLogLength() {
        return logLength;
    }

    private boolean packToJSON() {
        
        return true;
    }
}
