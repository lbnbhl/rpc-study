package github.lbnbhl.loadbalance;

import github.lbnbhl.remoting.dto.RpcRequest;
import github.lbnbhl.utils.CollectionUtil;

import java.util.List;

/**
 * Abstract class for a load balancing policy
 *
 * @author wwl
 * @createTime 2020年06月21日 07:44:00
 */
public abstract class AbstractLoadBalance implements LoadBalance {
    @Override
    public String selectServiceAddress(List<String> serviceAddresses, RpcRequest rpcRequest) {
        if (CollectionUtil.isEmpty(serviceAddresses)) {
            return null;
        }
        if (serviceAddresses.size() == 1) {
            return serviceAddresses.get(0);
        }
        return doSelect(serviceAddresses, rpcRequest);
    }

    protected abstract String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest);

}
