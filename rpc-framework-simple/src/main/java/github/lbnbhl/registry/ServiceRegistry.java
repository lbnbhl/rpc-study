package github.lbnbhl.registry;

import github.lbnbhl.extension.SPI;

import java.net.InetSocketAddress;

/**
 * service registration
 *
 * @author wwl
 * @createTime 2020年05月13日 08:39:00
 */
@SPI
public interface ServiceRegistry {
    /**
     * register service
     *
     * @param rpcServiceName    rpc service name
     * @param inetSocketAddress service address
     */
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);

}
