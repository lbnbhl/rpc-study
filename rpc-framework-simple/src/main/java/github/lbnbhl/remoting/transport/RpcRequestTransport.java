package github.lbnbhl.remoting.transport;

import github.lbnbhl.extension.SPI;
import github.lbnbhl.remoting.dto.RpcRequest;

/**
 * send RpcRequest。
 *
 * @author wwl
 * @createTime 2020年05月29日 13:26:00
 */
@SPI
public interface RpcRequestTransport {
    /**
     * send rpc request to server and get result
     *
     * @param rpcRequest message body
     * @return data from server
     */
    Object sendRpcRequest(RpcRequest rpcRequest);
}
