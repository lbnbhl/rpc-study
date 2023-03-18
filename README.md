## Rpc-Framework

### 项目介绍

------

基于 Netty+Kyro+Zookeeper 实现的 RPC 框架



#### 基于pom的构造分析

```java
//<artifactId>guide-rpc-framework</artifactId>
1.在属性中控制版本
2.聚合服务
3.可以使用lombok、guaua、log、test这些工具的依赖
    
//<artifactId>example-server</artifactId>
引入<artifactId>hello-service-api</artifactId>和<artifactId>rpc-framework-simple</artifactId>
    
//<artifactId>example-client</artifactId>
引入<artifactId>hello-service-api</artifactId>和<artifactId>rpc-framework-simple</artifactId>
    
//<artifactId>rpc-framework-simple</artifactId>
引入<artifactId>rpc-framework-common</artifactId>，网络传输框架netty，zookeeper客户端框架curator、序列化框架kryo以及spring等框架的依赖
    
//<artifactId>hello-service-api</artifactId>

//<artifactId>rpc-framework-common</artifactId>
```





#### 设计模式分析

- SingletonFactory类使用了单例模式
- 动态代理
- ServiceRegister类的SPI





#### 流程分析

##### SocketClientMain流程

> ```java
> public class SocketClientMain {
>     public static void main(String[] args) {
>         RpcRequestTransport rpcRequestTransport = new SocketRpcClient();
>         RpcServiceConfig rpcServiceConfig = new RpcServiceConfig();
>         RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcRequestTransport, rpcServiceConfig);
>         HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
>         String hello = helloService.hello(new Hello("111", "222"));
>         System.out.println(hello);
>     }
> }
> ```

1. new了一个RpcRequestTransport接口类型对象

   - 接口信息及实现

   <img src="C:\Users\Dear~柘木塘\AppData\Roaming\Typora\typora-user-images\image-20230318154132325.png" alt="image-20230318154132325" style="zoom:150%;" />

   - SocketRpcClient构造方法

     ~~~java
     //使用SPI模式，配置服务发现方式并且配置了负载均衡策略，这里通过zookeeper进行服务发现
     public SocketRpcClient() {
             this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class).getExtension(ServiceDiscoveryEnum.ZK.getName());
         }
     
     @SPI
     public interface ServiceDiscovery {
         /**
          * lookup service by rpcServiceName
          *
          * @param rpcRequest rpc service pojo
          * @return service address
          */
         InetSocketAddress lookupService(RpcRequest rpcRequest);
     }
     ~~~

2. new一个RPC服务配置对象RpcServiceConfig

   ~~~java
   public class RpcServiceConfig {
       /**
        * service version
        */
       private String version = "";
       /**
        * when the interface has multiple implementation classes, distinguish by group
        */
       private String group = "";
   
       /**
        * target service
        */
       private Object service;
   
       public String getRpcServiceName() {
           return this.getServiceName() + this.getGroup() + this.getVersion();
       }
   
       public String getServiceName() {
           return this.service.getClass().getInterfaces()[0].getCanonicalName();
       }
   }
   ~~~

3. new一个RpcClientProxy

   ![image-20230318162951133](C:\Users\Dear~柘木塘\AppData\Roaming\Typora\typora-user-images\image-20230318162951133.png)

4. 得到服务的代理对象

   > ```java
   > HelloService helloService = rpcClientProxy.getProxy(HelloService.class);
   > 
   > //使用的是JDK动态代理，TODO:可加入cglib动态代理
   >     public <T> T getProxy(Class<T> clazz) {
   >         return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
   >     }
   > ```

   

5. 代理对象调用远程服务的方法，执行invoke，进行远程调用，得到返回对象

   ~~~java
   //jdk动态代理，构建了RpcRequest，并发起远程调用
   public Object invoke(Object proxy, Method method, Object[] args) {
           log.info("invoked method: [{}]", method.getName());
           RpcRequest rpcRequest = RpcRequest.builder().methodName(method.getName())
                   .parameters(args)
                   .interfaceName(method.getDeclaringClass().getName())
                   .paramTypes(method.getParameterTypes())
                   .requestId(UUID.randomUUID().toString())
                   .group(rpcServiceConfig.getGroup())
                   .version(rpcServiceConfig.getVersion())
                   .build();
           RpcResponse<Object> rpcResponse = null;
           if (rpcRequestTransport instanceof NettyRpcClient) {
               CompletableFuture<RpcResponse<Object>> completableFuture = (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
               rpcResponse = completableFuture.get();
           }
           if (rpcRequestTransport instanceof SocketRpcClient) {
               rpcResponse = (RpcResponse<Object>) rpcRequestTransport.sendRpcRequest(rpcRequest);
           }
           this.check(rpcResponse, rpcRequest);
           return rpcResponse.getData();
       }
   
   
   //请求格式
   public class RpcRequest implements Serializable {
       private static final long serialVersionUID = 1905122041950251207L;
       private String requestId;
       private String interfaceName;
       private String methodName;
       private Object[] parameters;
       private Class<?>[] paramTypes;
       private String version;
       private String group;
   
       public String getRpcServiceName() {
           return this.getInterfaceName() + this.getGroup() + this.getVersion();
       }
   }
   
   //响应格式
   public class RpcResponse<T> implements Serializable {
   
       private static final long serialVersionUID = 715745410605631233L;
       private String requestId;
       /**
        * response code
        */
       private Integer code;
       /**
        * response message
        */
       private String message;
       /**
        * response body
        */
       private T data;
   
       public static <T> RpcResponse<T> success(T data, String requestId) {
           RpcResponse<T> response = new RpcResponse<>();
           response.setCode(RpcResponseCodeEnum.SUCCESS.getCode());
           response.setMessage(RpcResponseCodeEnum.SUCCESS.getMessage());
           response.setRequestId(requestId);
           if (null != data) {
               response.setData(data);
           }
           return response;
       }
   
       public static <T> RpcResponse<T> fail(RpcResponseCodeEnum rpcResponseCodeEnum) {
           RpcResponse<T> response = new RpcResponse<>();
           response.setCode(rpcResponseCodeEnum.getCode());
           response.setMessage(rpcResponseCodeEnum.getMessage());
           return response;
       }
   
   }
   
   
   //在rpcResponse = (RpcResponse<Object>) rpcRequestTransport.sendRpcRequest(rpcRequest);中，使用socket实现远程调用时
       @Override
       public Object sendRpcRequest(RpcRequest rpcRequest) {
           InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcRequest);
           try (Socket socket = new Socket()) {
               socket.connect(inetSocketAddress);
               //TODO：可使用其他序列化方式
               ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
               // Send data to the server through the output stream
               objectOutputStream.writeObject(rpcRequest);
               ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
               // Read RpcResponse from the input stream
               return objectInputStream.readObject();
           } catch (IOException | ClassNotFoundException e) {
               throw new RpcException("调用服务失败:", e);
           }
       }
   
   //在serviceDiscovery.lookupService(rpcRequest)中，查询请求对应服务注册地址
       @Override
       public InetSocketAddress lookupService(RpcRequest rpcRequest) {
           String rpcServiceName = rpcRequest.getRpcServiceName();
           CuratorFramework zkClient = CuratorUtils.getZkClient();
           List<String> serviceUrlList = CuratorUtils.getChildrenNodes(zkClient, rpcServiceName);
           if (CollectionUtil.isEmpty(serviceUrlList)) {
               throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND, rpcServiceName);
           }
           // load balancing
           String targetServiceUrl = loadBalance.selectServiceAddress(serviceUrlList, rpcRequest);
           log.info("Successfully found the service address:[{}]", targetServiceUrl);
           String[] socketAddressArray = targetServiceUrl.split(":");
           String host = socketAddressArray[0];
           int port = Integer.parseInt(socketAddressArray[1]);
           return new InetSocketAddress(host, port);
       }
   
   ~~~

##### SocketServerMain流程

> ```java
> public static void main(String[] args) {
>     //TODO 可以把要上线的服务通过注解扫描出来再注册到zookeeper上
>     HelloService helloService = new HelloServiceImpl();
>     SocketRpcServer socketRpcServer = new SocketRpcServer();
>     RpcServiceConfig rpcServiceConfig = new RpcServiceConfig();
>     rpcServiceConfig.setService(helloService);
>     socketRpcServer.registerService(rpcServiceConfig);
>     socketRpcServer.start();
> }
> 
> ```

1. **HelloService helloService = new HelloServiceImpl();**

   > 创建要注册的服务的对象

2. **SocketRpcServer socketRpcServer = new SocketRpcServer();**

   > 创建SocketRpcServer对象

   ~~~java
   //提供了服务的注册registerService(RpcServiceConfig rpcServiceConfig，和服务器的开启功能start()，并且创建了执行请求任务的线程池ExecutorService和服务提供者ServiceProvider接口的实现类ZkServiceProviderImpl的单例对象,而ZkServiceProviderImpl对象创建时，使用SPI模式，配置服务注册方式，这里通过zookeeper进行服务注册。
   public class SocketRpcServer {
   
       private final ExecutorService threadPool;
       private final ServiceProvider serviceProvider;
   
   
       public SocketRpcServer() {
           threadPool = ThreadPoolFactoryUtil.createCustomThreadPoolIfAbsent("socket-server-rpc-pool");
           serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
       }
   
       //注册服务
       public void registerService(RpcServiceConfig rpcServiceConfig) {
           serviceProvider.publishService(rpcServiceConfig);
       }
   
       //开启服务器，绑定端口，监听事件
       public void start() {
           try (ServerSocket server = new ServerSocket()) {
               String host = InetAddress.getLocalHost().getHostAddress();
               server.bind(new InetSocketAddress(host, PORT));
               CustomShutdownHook.getCustomShutdownHook().clearAll();
               Socket socket;
               while ((socket = server.accept()) != null) {
                   log.info("client connected [{}]", socket.getInetAddress());
                   threadPool.execute(new SocketRpcRequestHandlerRunnable(socket));
               }
               threadPool.shutdown();
           } catch (IOException e) {
               log.error("occur IOException:", e);
           }
       }
   
   }
   
   //使用SPI模式，配置服务注册方式，这里通过zookeeper进行服务注册
       public ZkServiceProviderImpl() {
           serviceMap = new ConcurrentHashMap<>();
           registeredService = ConcurrentHashMap.newKeySet();
           serviceRegistry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class).getExtension(ServiceRegistryEnum.ZK.getName());
       }
   
   ~~~

3. **RpcServiceConfig rpcServiceConfig = new RpcServiceConfig();**

   > 创建**RpcServiceConfig**对象

   ~~~ java
   public class RpcServiceConfig {
       /**
        * service version
        */
       private String version = "";
       /**
        * when the interface has multiple implementation classes, distinguish by group
        */
       private String group = "";
   
       /**
        * target service
        */
       private Object service;
   
       public String getRpcServiceName() {
           return this.getServiceName() + this.getGroup() + this.getVersion();
       }
   
       public String getServiceName() {
           return this.service.getClass().getInterfaces()[0].getCanonicalName();
       }
   }
   
   ~~~

4. **rpcServiceConfig.setService(helloService);**

   > 将要注册的服务helloService注入到rpcServiceConfig中

5. **socketRpcServer.registerService(rpcServiceConfig);**

   > 注册rpcServiceConfig服务

   ~~~ java
       public void registerService(RpcServiceConfig rpcServiceConfig) {
           serviceProvider.publishService(rpcServiceConfig);
       }
   
   
       @Override
       public void publishService(RpcServiceConfig rpcServiceConfig) {
           try {
               String host = InetAddress.getLocalHost().getHostAddress();
               this.addService(rpcServiceConfig);
               serviceRegistry.registerService(rpcServiceConfig.getRpcServiceName(), new InetSocketAddress(host, NettyRpcServer.PORT));
           } catch (UnknownHostException e) {
               log.error("occur exception when getHostAddress", e);
           }
       }
   
   
       @Override
       public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress) {
           String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + inetSocketAddress.toString();
           CuratorFramework zkClient = CuratorUtils.getZkClient();
           CuratorUtils.createPersistentNode(zkClient, servicePath);
       }
   ~~~

6. **socketRpcServer.start();**

   > 启动服务器

   ~~~ java
       //开启服务器，绑定端口，监听事件
       public void start() {
           try (ServerSocket server = new ServerSocket()) {
               String host = InetAddress.getLocalHost().getHostAddress();
               server.bind(new InetSocketAddress(host, PORT));
               CustomShutdownHook.getCustomShutdownHook().clearAll();
               Socket socket;
               while ((socket = server.accept()) != null) {
                   log.info("client connected [{}]", socket.getInetAddress());
                   threadPool.execute(new SocketRpcRequestHandlerRunnable(socket));
               }
               threadPool.shutdown();
           } catch (IOException e) {
               log.error("occur IOException:", e);
           }
       }
   
   }
   
   
   //CustomShutdownHook.getCustomShutdownHook().clearAll();,钩子函数的使用，服务器关闭时，撤销注册，并关闭线程池
       public void clearAll() {
           log.info("addShutdownHook for clearAll");
           Runtime.getRuntime().addShutdownHook(new Thread(() -> {
               try {
                   InetSocketAddress inetSocketAddress = new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), NettyRpcServer.PORT);
                   CuratorUtils.clearRegistry(CuratorUtils.getZkClient(), inetSocketAddress);
               } catch (UnknownHostException ignored) {
               }
               ThreadPoolFactoryUtil.shutDownAllThreadPool();
           }));
       }
   ~~~

7. **while ((socket = server.accept()) != null)** 

   > 监听到连接事件

   ~~~ java
   //监听到事件时，提交线程池，执行任务
       while ((socket = server.accept()) != null) {
                   log.info("client connected [{}]", socket.getInetAddress());
                   threadPool.execute(new SocketRpcRequestHandlerRunnable(socket));
               }
   //获取到rpcRequestHandler的单例，然后执行run()
       public SocketRpcRequestHandlerRunnable(Socket socket) {
           this.socket = socket;
           this.rpcRequestHandler = SingletonFactory.getInstance(RpcRequestHandler.class);
       }
   
   
   //获取字节流，封装成请求，然后处理请求，调用rpcRequestHandler.handle(rpcRequest)，得到结果后封装成RpcResponse返回
       @Override
       public void run() {
           log.info("server handle message from client by thread: [{}]", Thread.currentThread().getName());
           //可用其他序列化方式代替
           try (ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream())) {
               RpcRequest rpcRequest = (RpcRequest) objectInputStream.readObject();
               //处理请求
               Object result = rpcRequestHandler.handle(rpcRequest);
               objectOutputStream.writeObject(RpcResponse.success(result, rpcRequest.getRequestId()));
               objectOutputStream.flush();
           } catch (IOException | ClassNotFoundException e) {
               log.error("occur exception:", e);
           }
       }
   
   
   //得到请求服务名getRpcServiceName，反射调用invokeTargetMethod(rpcRequest, service);
   public class RpcRequestHandler {
       private final ServiceProvider serviceProvider;
   
       public RpcRequestHandler() {
           serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
       }
   
       /**
        * Processing rpcRequest: call the corresponding method, and then return the method
        */
       public Object handle(RpcRequest rpcRequest) {
           Object service = serviceProvider.getService(rpcRequest.getRpcServiceName());
           return invokeTargetMethod(rpcRequest, service);
       }
   
       /**
        * get method execution results
        *
        * @param rpcRequest client request
        * @param service    service object
        * @return the result of the target method execution
        */
       private Object invokeTargetMethod(RpcRequest rpcRequest, Object service) {
           Object result;
           try {
               Method method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamTypes());
               result = method.invoke(service, rpcRequest.getParameters());
               log.info("service:[{}] successful invoke method:[{}]", rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
           } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
               throw new RpcException(e.getMessage(), e);
           }
           return result;
       }
   }
   
   ~~~




##### NettyClientMain流程

> ```java
> @RpcScan(basePackage = {"github.lbnbhl"})
> public class NettyServerMain {
>     public static void main(String[] args) {
>     
>         // Register service via annotation
>         AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
>         
>         NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");
>         
>         // Register service manually
>         HelloService helloService2 = new HelloServiceImpl2();
>         
>         RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
>                 .group("test2").version("version2").service(helloService2).build();
>                 
>         nettyRpcServer.registerService(rpcServiceConfig);
>         
>         nettyRpcServer.start();
>     }
> }
> ```

1. **new AnnotationConfigApplicationContext(NettyClientMain.class);**

   > 将NettyClientMain作为配置类

   ~~~ Java
   @RpcScan(basePackage = {"github.lbnbhl"})
   public class NettyClientMain{}
   
   //加载@RpcScan注解
   @Target({ElementType.TYPE, ElementType.METHOD})
   @Retention(RetentionPolicy.RUNTIME)
   @Import(CustomScannerRegistrar.class)
   @Documented
   public @interface RpcScan {
   
       String[] basePackage();
   
   }
   
   //导入CustomScannerRegistrar组件类，扫描rpcscan的属性basepackage下的注解
   @Slf4j
   public class CustomScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
       private static final String SPRING_BEAN_BASE_PACKAGE = "github.lbnbhl";
       private static final String BASE_PACKAGE_ATTRIBUTE_NAME = "basePackage";
       private ResourceLoader resourceLoader;
   
       @Override
       public void setResourceLoader(ResourceLoader resourceLoader) {
           this.resourceLoader = resourceLoader;
   
       }
   
       @Override
       public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
           //得到rpc注解的属性和值
           AnnotationAttributes rpcScanAnnotationAttributes = AnnotationAttributes.fromMap(annotationMetadata.getAnnotationAttributes(RpcScan.class.getName()));
           String[] rpcScanBasePackages = new String[0];
           if (rpcScanAnnotationAttributes != null) {
               // 得到basepackage的值
               rpcScanBasePackages = rpcScanAnnotationAttributes.getStringArray(BASE_PACKAGE_ATTRIBUTE_NAME);
           }
           if (rpcScanBasePackages.length == 0) {
               rpcScanBasePackages = new String[]{((StandardAnnotationMetadata) annotationMetadata).getIntrospectedClass().getPackage().getName()};
           }
           // Scan the RpcService annotation
           CustomScanner rpcServiceScanner = new CustomScanner(beanDefinitionRegistry, RpcService.class);
           // Scan the Component annotation
           CustomScanner springBeanScanner = new CustomScanner(beanDefinitionRegistry, Component.class);
           if (resourceLoader != null) {
               rpcServiceScanner.setResourceLoader(resourceLoader);
               springBeanScanner.setResourceLoader(resourceLoader);
           }
           int springBeanAmount = springBeanScanner.scan(SPRING_BEAN_BASE_PACKAGE);
           log.info("springBeanScanner扫描的数量 [{}]", springBeanAmount);
           int rpcServiceCount = rpcServiceScanner.scan(rpcScanBasePackages);
           log.info("rpcServiceScanner扫描的数量 [{}]", rpcServiceCount);
   
       }
   
   }
   ~~~

   

2. **HelloController helloController = (HelloController) applicationContext.getBean("helloController");**

   > 获取bean对象

3. spring的后置处理器

   ~~~java
   @Slf4j
   @Component
   public class SpringBeanPostProcessor implements BeanPostProcessor {
   
       private final ServiceProvider serviceProvider;
       private final RpcRequestTransport rpcClient;
   
       public SpringBeanPostProcessor() {
           this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
           //1.以spi方式，加载了NettyRpcClient，初始化了Bootstrap
           this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class).getExtension(RpcRequestTransportEnum.NETTY.getName());
       }
   
       //2.初始化方法执行之前
       @SneakyThrows
       @Override
       public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
           //如果是RpcService类型的Bean就注册到zookeeper上
           if (bean.getClass().isAnnotationPresent(RpcService.class)) {
               log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
               // get RpcService annotation
               RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
               // build RpcServiceProperties
               RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                       .group(rpcService.group())
                       .version(rpcService.version())
                       .service(bean).build();
               serviceProvider.publishService(rpcServiceConfig);
           }
           return bean;
       }
   
       
       //3.初始化方法执行之后
       @Override
       public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
           Class<?> targetClass = bean.getClass();
           Field[] declaredFields = targetClass.getDeclaredFields();
           for (Field declaredField : declaredFields) {
               RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);
               //如果这个bean有属性使用了@RpcReference注释，new一个RpcClientProxy，然后使用代理对象
               if (rpcReference != null) {
                   RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                           .group(rpcReference.group())
                           .version(rpcReference.version()).build();
                   RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceConfig);
                   Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                   declaredField.setAccessible(true);
                   try {
                       declaredField.set(bean, clientProxy);
                   } catch (IllegalAccessException e) {
                       e.printStackTrace();
                   }
               }
   
           }
           return bean;
       }
   }
   
   
   
   //动态代理类，构建请求，如果以NettyRpcClient方式传输，使用 CompletableFuture 包装接受客户端返回结果
   @Slf4j
   public class RpcClientProxy implements InvocationHandler {
   
       private static final String INTERFACE_NAME = "interfaceName";
   
       /**
        * Used to send requests to the server.And there are two implementations: socket and netty
        */
       private final RpcRequestTransport rpcRequestTransport;
       private final RpcServiceConfig rpcServiceConfig;
   
       public RpcClientProxy(RpcRequestTransport rpcRequestTransport, RpcServiceConfig rpcServiceConfig) {
           this.rpcRequestTransport = rpcRequestTransport;
           this.rpcServiceConfig = rpcServiceConfig;
       }
   
   
       public RpcClientProxy(RpcRequestTransport rpcRequestTransport) {
           this.rpcRequestTransport = rpcRequestTransport;
           this.rpcServiceConfig = new RpcServiceConfig();
       }
   
       /**
        * get the proxy object
        */
       @SuppressWarnings("unchecked")
       public <T> T getProxy(Class<T> clazz) {
           //TODO:可加入cglib动态代理
           return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
       }
   
       /**
        * This method is actually called when you use a proxy object to call a method.
        * The proxy object is the object you get through the getProxy method.
        */
       @SneakyThrows
       @SuppressWarnings("unchecked")
       @Override
       public Object invoke(Object proxy, Method method, Object[] args) {
           log.info("invoked method: [{}]", method.getName());
           RpcRequest rpcRequest = RpcRequest.builder().methodName(method.getName())
                   .parameters(args)
                   .interfaceName(method.getDeclaringClass().getName())
                   .paramTypes(method.getParameterTypes())
                   .requestId(UUID.randomUUID().toString())
                   .group(rpcServiceConfig.getGroup())
                   .version(rpcServiceConfig.getVersion())
                   .build();
           RpcResponse<Object> rpcResponse = null;
           if (rpcRequestTransport instanceof NettyRpcClient) {
               CompletableFuture<RpcResponse<Object>> completableFuture = (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
               rpcResponse = completableFuture.get();
           }
           if (rpcRequestTransport instanceof SocketRpcClient) {
               rpcResponse = (RpcResponse<Object>) rpcRequestTransport.sendRpcRequest(rpcRequest);
           }
           this.check(rpcResponse, rpcRequest);
           return rpcResponse.getData();
       }
   
       private void check(RpcResponse<Object> rpcResponse, RpcRequest rpcRequest) {
           if (rpcResponse == null) {
               throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
           }
   
           if (!rpcRequest.getRequestId().equals(rpcResponse.getRequestId())) {
               throw new RpcException(RpcErrorMessageEnum.REQUEST_NOT_MATCH_RESPONSE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
           }
   
           if (rpcResponse.getCode() == null || !rpcResponse.getCode().equals(RpcResponseCodeEnum.SUCCESS.getCode())) {
               throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
           }
       }
   }
   
   ~~~

   

4. helloController.test();

   > 执行方法test()

   ~~~ java
   @Component
   public class HelloController {
   
       @RpcReference(version = "version1", group = "test1")
       private HelloService helloService;
   
       public void test() throws InterruptedException {
           String hello = this.helloService.hello(new Hello("111", "222"));
           //如需使用 assert 断言，需要在 VM options 添加参数：-ea
           assert "Hello description is 222".equals(hello);
           Thread.sleep(12000);
           for (int i = 0; i < 10; i++) {
               System.out.println(helloService.hello(new Hello("111", "222")));
           }
       }
   }
   ~~~

   

##### NettyServerMain流程

> ```Java
> @RpcScan(basePackage = {"github.lbnbhl"})
> public class NettyServerMain {
>     public static void main(String[] args) {
>         // Register service via annotation
>         AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
>         NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");
>         // Register service manually
>         HelloService helloService2 = new HelloServiceImpl2();
>         RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
>                 .group("test2").version("version2").service(helloService2).build();
>         nettyRpcServer.registerService(rpcServiceConfig);
>         nettyRpcServer.start();
>     }
> }
> ```



1. **applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);**

   > 将NettyServerMain作为配置类扫描

   ~~~Java
   @RpcScan(basePackage = {"github.lbnbhl"})
   public class NettyServerMain
       
   //@RpcScan导入了@Import(CustomScannerRegistrar.class)，会扫描扫描rpcscan的属性basepackage下的注解
   @Target({ElementType.TYPE, ElementType.METHOD})
   @Retention(RetentionPolicy.RUNTIME)
   @Import(CustomScannerRegistrar.class)
   @Documented
   public @interface RpcScan {
   
       String[] basePackage();
   
   }
   ~~~

2. **NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");**

   > 得到nettyRpcServer 对象，可创建ServiceProvider，然后调用registerService注册服务，还可以调用start，启动服务器的功能，完成端口的监听及事件的处理，并在关闭后实现服务的下线

   ~~~ Java
   @Component
   public class NettyRpcServer {
   
       public static final int PORT = 9998;
   
       private final ServiceProvider serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
   
       public void registerService(RpcServiceConfig rpcServiceConfig) {
           serviceProvider.publishService(rpcServiceConfig);
       }
   
       @SneakyThrows
       public void start() {
           CustomShutdownHook.getCustomShutdownHook().clearAll();
           String host = InetAddress.getLocalHost().getHostAddress();
           EventLoopGroup bossGroup = new NioEventLoopGroup(1);
           EventLoopGroup workerGroup = new NioEventLoopGroup();
           DefaultEventExecutorGroup serviceHandlerGroup = new DefaultEventExecutorGroup(
                   RuntimeUtil.cpus() * 2,
                   ThreadPoolFactoryUtil.createThreadFactory("service-handler-group", false)
           );
           try {
               ServerBootstrap b = new ServerBootstrap();
               b.group(bossGroup, workerGroup)
                       .channel(NioServerSocketChannel.class)
                       // TCP默认开启了 Nagle 算法，该算法的作用是尽可能的发送大数据快，减少网络传输。TCP_NODELAY 参数的作用就是控制是否启用 Nagle 算法。
                       .childOption(ChannelOption.TCP_NODELAY, true)
                       // 是否开启 TCP 底层心跳机制
                       .childOption(ChannelOption.SO_KEEPALIVE, true)
                       //表示系统用于临时存放已完成三次握手的请求的队列的最大长度,如果连接建立频繁，服务器处理创建新连接较慢，可以适当调大这个参数
                       .option(ChannelOption.SO_BACKLOG, 128)
                       .handler(new LoggingHandler(LogLevel.INFO))
                       // 当客户端第一次进行请求的时候才会进行初始化
                       .childHandler(new ChannelInitializer<SocketChannel>() {
                           @Override
                           protected void initChannel(SocketChannel ch) {
                               // 30 秒之内没有收到客户端请求的话就关闭连接
                               ChannelPipeline p = ch.pipeline();
                               p.addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS));
                               p.addLast(new RpcMessageEncoder());
                               p.addLast(new RpcMessageDecoder());
                               p.addLast(serviceHandlerGroup, new NettyRpcServerHandler());
                           }
                       });
   
               // 绑定端口，同步等待绑定成功
               ChannelFuture f = b.bind(host, PORT).sync();
               // 等待服务端监听端口关闭
               f.channel().closeFuture().sync();
           } catch (InterruptedException e) {
               log.error("occur exception when start server:", e);
           } finally {
               log.error("shutdown bossGroup and workerGroup");
               bossGroup.shutdownGracefully();
               workerGroup.shutdownGracefully();
               serviceHandlerGroup.shutdownGracefully();
           }
       }
   
   
   }
   ~~~

3. **spring的后置处理器**

   ~~~ java
   //postProcessBeforeInitialization()
   初始化方法之前：如果是RpcService类型注解过的的Bean就注册到zookeeper上
   //postProcessAfterInitialization
   初始化方法执行之后：如果这个bean有属性使用了@RpcReference注释，new一个RpcClientProxy，使用代理对象
   ~~~

4. **HelloService helloService2 = new HelloServiceImpl2();**

   > 创建服务对象

5. **rpcServiceConfig = RpcServiceConfig.builder().group("test2").version("version2").service(helloService2).build();**

   > 构建服务配置属性

6. **nettyRpcServer.registerService(rpcServiceConfig);**

   > 手动注册服务

7. **nettyRpcServer.start();**

   > 启动服务器



