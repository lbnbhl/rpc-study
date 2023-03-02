package github.lbnbhl.provider;

import github.lbnbhl.config.RpcServiceConfig;

/**
 * store and provide service object.
 *
 * @author wwl
 * @createTime 2020年05月31日 16:52:00
 */
public interface ServiceProvider {

    /**
     * @param rpcServiceConfig rpc service related attributes
     */
    void addService(RpcServiceConfig rpcServiceConfig);

    /**
     * @param rpcServiceName rpc service name
     * @return service object
     */
    Object getService(String rpcServiceName);

    /**
     * @param rpcServiceConfig rpc service related attributes
     */
    void publishService(RpcServiceConfig rpcServiceConfig);

}
