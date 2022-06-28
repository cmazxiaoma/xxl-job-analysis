写给未来的自己

关于executor端是  开启一个jettyServer 其中配置了JettyServerHandler
将请求交给XxlRpcResponse xxlRpcResponse = xxlRpcProviderFactory.invokeService(xxlRpcRequest);



关于admin端 其实是没有开启jettyServer, 是直接暴露一个api接口
接口中调用XxlJobDynamicScheduler.invokeAdminService(request, response);
来达到和executor端类似的效果,请求交给JettyServerHandler处理, 委托给xxlRpcProviderFacotry调用本地方法


XxlRpcReferenceBean 这个factoryBean中的getObject方法 创建的代理对象，
     * 代理逻辑中过滤掉了非业务方法,也就是Object类中的方法
     * 只是将目标类的方法名，参数，类名等信息包装成XxlRpcRequest 通过JettyClient发送给调度中心
     * 发送的地址调度中心的接口地址是 ：“调度中心IP/api” 这个接.这个是在执行器启动的时候初始化设置好的。
     * 调度中心的API接口拿到请求之后，通过参数里面的类名,方法,参数 反射出来一个对象,然后invoke。
 
每个XxlRpcReferenceBean对象中都会初始化一个JettyClient对象  
  
```
	Client client = null;

	private void initClient() {
		try {
			client = netType.clientClass.newInstance();
			client.init(this);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new XxlRpcException(e);
		}
	}
```

```
return Proxy.newProxyInstance(Thread.currentThread()
				.getContextClassLoader(), new Class[] { iface },
				new InvocationHandler() {
					@Override
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						String className = method.getDeclaringClass().getName();

						// filter method like "Object.toString()"
						if (Object.class.getName().equals(className)) {
							logger.info(">>>>>>>>>>> xxl-rpc proxy class-method not support [{}.{}]", className, method.getName());
							throw new XxlRpcException("xxl-rpc proxy class-method not support");
						}

						// address
						String address = routeAddress();
						if (address==null || address.trim().length()==0) {
							throw new XxlRpcException("xxl-rpc reference bean["+ className +"] address empty");
						}

						// request
						XxlRpcRequest xxlRpcRequest = new XxlRpcRequest();
	                    xxlRpcRequest.setRequestId(UUID.randomUUID().toString());
	                    xxlRpcRequest.setCreateMillisTime(System.currentTimeMillis());
	                    xxlRpcRequest.setAccessToken(accessToken);
	                    xxlRpcRequest.setClassName(className);
	                    xxlRpcRequest.setMethodName(method.getName());
	                    xxlRpcRequest.setParameterTypes(method.getParameterTypes());
	                    xxlRpcRequest.setParameters(args);
	                    
	                    // send
						if (CallType.SYNC == callType) {
							try {
								// future set
								XxlRpcFutureResponse futureResponse = new XxlRpcFutureResponse(xxlRpcRequest, null);

								// do invoke
								client.asyncSend(address, xxlRpcRequest);

								// future get
								XxlRpcResponse xxlRpcResponse = futureResponse.get(timeout, TimeUnit.MILLISECONDS);
								if (xxlRpcResponse.getErrorMsg() != null) {
									throw new XxlRpcException(xxlRpcResponse.getErrorMsg());
								}
								return xxlRpcResponse.getResult();
							} catch (Exception e) {
								logger.info(">>>>>>>>>>> xxl-job, invoke error, address:{}, XxlRpcRequest{}", address, xxlRpcRequest);

								throw (e instanceof XxlRpcException)?e:new XxlRpcException(e);
							} finally{
								// remove-InvokerFuture
                                XxlRpcFutureResponseFactory.removeInvokerFuture(xxlRpcRequest.getRequestId());
							}
						} else if (CallType.FUTURE == callType) {

							// thread future set
							XxlRpcInvokeFuture invokeFuture = null;
                            try {
								// future set
								invokeFuture = new XxlRpcInvokeFuture(new XxlRpcFutureResponse(xxlRpcRequest, null));
								XxlRpcInvokeFuture.setFuture(invokeFuture);

                                // do invoke
                                client.asyncSend(address, xxlRpcRequest);

                                return null;
                            } catch (Exception e) {
								logger.info(">>>>>>>>>>> xxl-job, invoke error, address:{}, XxlRpcRequest{}", address, xxlRpcRequest);

								// remove-InvokerFuture
								invokeFuture.stop();

								throw (e instanceof XxlRpcException)?e:new XxlRpcException(e);
                            }

						} else if (CallType.CALLBACK == callType) {

							// get callback
							XxlRpcInvokeCallback finalInvokeCallback = invokeCallback;
							XxlRpcInvokeCallback threadInvokeCallback = XxlRpcInvokeCallback.getCallback();
							if (threadInvokeCallback != null) {
								finalInvokeCallback = threadInvokeCallback;
							}
							if (finalInvokeCallback == null) {
								throw new XxlRpcException("xxl-rpc XxlRpcInvokeCallback（CallType="+ CallType.CALLBACK.name() +"） cannot be null.");
							}

							try {
								// future set
								XxlRpcFutureResponse futureResponse = new XxlRpcFutureResponse(xxlRpcRequest, finalInvokeCallback);

								client.asyncSend(address, xxlRpcRequest);
							} catch (Exception e) {
								logger.info(">>>>>>>>>>> xxl-job, invoke error, address:{}, XxlRpcRequest{}", address, xxlRpcRequest);

								// future remove
								XxlRpcFutureResponseFactory.removeInvokerFuture(xxlRpcRequest.getRequestId());

								throw (e instanceof XxlRpcException)?e:new XxlRpcException(e);
							}

							return null;
						} else if (CallType.ONEWAY == callType) {
                            client.asyncSend(address, xxlRpcRequest);
                            return null;
                        } else {
							throw new XxlRpcException("xxl-rpc callType["+ callType +"] invalid");
						}

					}
				});
	}
```

当我们请求模式是SYNC模式时, 这里只讲SYNC模式

XxlRpcFutureResponse futureResponse = new XxlRpcFutureResponse(xxlRpcRequest, null);

将请求包装成XxlRpcFutureResponse对象, 这里的invokeCallback回调是null

```
	public XxlRpcFutureResponse(XxlRpcRequest request, XxlRpcInvokeCallback invokeCallback) {
		this.request = request;
		this.invokeCallback = invokeCallback;

		// set-InvokerFuture
		XxlRpcFutureResponseFactory.setInvokerFuture(request.getRequestId(), this);
	}
```

当我们获取结果时, done变量如果是false, 一直阻塞线程(还有超时机制), 除非有调用setResponse(XxlRpcResponse response)方法
使done变量为true, 并获取锁,调用lock.notifyAll(), 唤醒线程,并返回执行结果。

在JettyClient中发送请求时

	@Override
	public void asyncSend(String address, XxlRpcRequest xxlRpcRequest) throws Exception {
		// do invoke
		postRequestAsync(address, xxlRpcRequest);
	}
	
在postRequestAsync方法中

```
// deserialize response
XxlRpcResponse xxlRpcResponse = (XxlRpcResponse) xxlRpcReferenceBean.getSerializer().deserialize(responseBytes, XxlRpcResponse.class);
// notify response
XxlRpcFutureResponseFactory.notifyInvokerFuture(xxlRpcResponse.getRequestId(), xxlRpcResponse);
```


在初始化XxlRpcFutureResponse中, 我们有调用setInvokerFuture方法将消息和XxlRpcFutureResponse结果维护起来

当在JettyClient执行完请求获取结果时, 调用notifyInvokerFuture方法设置XxlRpcFutureResponse中的xxlRpcResponse属性
也就是真实的执行结果。


JettyClient方法请求虽然是异步的, 但是这里还是同步阻塞获取执行结果



```
public class XxlRpcFutureResponseFactory {

    private static ConcurrentMap<String, XxlRpcFutureResponse> futureResponsePool = new ConcurrentHashMap<String, XxlRpcFutureResponse>();

    public static void setInvokerFuture(String requestId, XxlRpcFutureResponse futureResponse){
        // TODO，running future method-isolation and limit
        futureResponsePool.put(requestId, futureResponse);
    }

    public static void removeInvokerFuture(String requestId){
        futureResponsePool.remove(requestId);
    }

    public static void notifyInvokerFuture(String requestId, XxlRpcResponse xxlRpcResponse){
        XxlRpcFutureResponse futureResponse = futureResponsePool.get(requestId);
        if (futureResponse != null) {
            futureResponse.setResponse(xxlRpcResponse);
            futureResponsePool.remove(requestId);
        }
    }

}
```

