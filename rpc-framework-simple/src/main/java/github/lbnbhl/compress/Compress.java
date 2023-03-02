package github.lbnbhl.compress;

import github.lbnbhl.extension.SPI;

/**
 * @author wwl .
 * @createTime on 2020/10/3
 */

@SPI
public interface Compress {

    byte[] compress(byte[] bytes);


    byte[] decompress(byte[] bytes);
}
