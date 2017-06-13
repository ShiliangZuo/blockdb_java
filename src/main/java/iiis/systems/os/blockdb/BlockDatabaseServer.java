package iiis.systems.os.blockdb;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;

public class BlockDatabaseServer {
    private Server server;

    private static ManagedChannel channel;
    private static BlockChainMinerGrpc.BlockChainMinerBlockingStub blockingStub;
    private static BlockChainMinerGrpc.BlockChainMinerStub asyncStub;
    private static JSONObject config;
    private static int nServers;
    private static String serverId;


    private void start(String address, int port) throws IOException {
        server = NettyServerBuilder.forAddress(new InetSocketAddress(address, port))
                .addService(new BlockDatabaseImpl())
                .build()
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                BlockDatabaseServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, JSONException, InterruptedException {
        config = Util.readJsonFile("config.json");
        nServers = config.getInt("nservers");



        JSONObject thisServer = (JSONObject)config.get(serverId);
        String address = thisServer.getString("ip");
        int port = Integer.parseInt(thisServer.getString("port"));
        String dataDir = thisServer.getString("dataDir");

        DatabaseEngine.setup(dataDir);

        final BlockDatabaseServer server = new BlockDatabaseServer();
        System.out.println("Server about to start!");
        server.start(address, port);
        server.blockUntilShutdown();
    }

    static class BlockDatabaseImpl extends BlockChainMinerGrpc.BlockChainMinerImplBase {

        private final DatabaseEngine dbEngine = DatabaseEngine.getInstance();

        @Override
        public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
            int value = dbEngine.get(request.getUserID());
            GetResponse response = GetResponse.newBuilder().setValue(value).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void transfer(Transaction request, StreamObserver<BooleanResponse> responseObserver) {
            boolean success = dbEngine.transfer(request);
            BooleanResponse response = BooleanResponse.newBuilder().setSuccess(success).build();

            for (int i = 1; i <= nServers; ++i) {
                if (i == Integer.parseInt(serverId)) {
                    continue;
                }
                JSONObject targetServer = (JSONObject) config.get(Integer.toString(i));

                String address = targetServer.getString("ip");
                int port = Integer.parseInt(targetServer.getString("port"));

                channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext(true).build();
                blockingStub = BlockChainMinerGrpc.newBlockingStub(channel);
                asyncStub = BlockChainMinerGrpc.newStub(channel);
                StreamObserver<Null> observer = new StreamObserver<Null>() {
                    @Override
                    public void onNext(Null aNull) {

                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onCompleted() {

                    }
                };
                asyncStub.pushTransaction(request, observer);
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void pushTransaction(Transaction request,
                                    StreamObserver<Null> responseObserver) {
            dbEngine.receive(request);
            Null response = Null.newBuilder().build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        /*@Override
        public void put(Request request, StreamObserver<BooleanResponse> responseObserver) {
            boolean success = dbEngine.put(request.getUserID(), request.getValue());
            BooleanResponse response = BooleanResponse.newBuilder().setSuccess(success).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void withdraw(Request request, StreamObserver<BooleanResponse> responseObserver) {
            boolean success = dbEngine.withdraw(request.getUserID(), request.getValue());
            BooleanResponse response = BooleanResponse.newBuilder().setSuccess(success).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void deposit(Request request, StreamObserver<BooleanResponse> responseObserver) {
            boolean success = dbEngine.deposit(request.getUserID(), request.getValue());
            BooleanResponse response = BooleanResponse.newBuilder().setSuccess(success).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }*/

        /*
        @Override
        public void transfer(TransferRequest request, StreamObserver<BooleanResponse> responseObserver) {
            boolean success = dbEngine.transfer(request.getFromID(), request.getToID(), request.getValue());
            BooleanResponse response = BooleanResponse.newBuilder().setSuccess(success).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void logLength(Null request, StreamObserver<GetResponse> responseObserver) {
            int value = dbEngine.getLogLength();
            GetResponse response = GetResponse.newBuilder().setValue(value).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        */

    }
}
