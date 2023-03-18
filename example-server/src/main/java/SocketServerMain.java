import github.lbnbhl.HelloService;
import github.lbnbhl.config.RpcServiceConfig;
import github.lbnbhl.remoting.transport.socket.SocketRpcServer;
import github.lbnbhl.serviceimpl.HelloServiceImpl;

/**
 * @author wwl
 * @createTime 2020年05月10日 07:25:00
 * socket方式服务端
 */
public class SocketServerMain {
    public static void main(String[] args) {
        //TODO 可以把要上线的服务通过注解扫描出来再注册到zookeeper上
        HelloService helloService = new HelloServiceImpl();
        SocketRpcServer socketRpcServer = new SocketRpcServer();
        RpcServiceConfig rpcServiceConfig = new RpcServiceConfig();
        rpcServiceConfig.setService(helloService);
        socketRpcServer.registerService(rpcServiceConfig);
        socketRpcServer.start();
    }
}
