import github.lbnbhl.HelloService;
import github.lbnbhl.annotation.RpcScan;
import github.lbnbhl.config.RpcServiceConfig;
import github.lbnbhl.remoting.transport.netty.server.NettyRpcServer;
import github.lbnbhl.serviceimpl.HelloServiceImpl2;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Server: Automatic registration service via @RpcService annotation
 *
 * @author wwl
 * @createTime 2020年05月10日 07:25:00
 */
@RpcScan(basePackage = {"github.lbnbhl"})
public class NettyServerMain {
    public static void main(String[] args) {
        // Register service via annotation
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
        NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");
        // Register service manually
        HelloService helloService2 = new HelloServiceImpl2();
        RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                .group("test2").version("version2").service(helloService2).build();
        nettyRpcServer.registerService(rpcServiceConfig);
        nettyRpcServer.start();
    }
}
