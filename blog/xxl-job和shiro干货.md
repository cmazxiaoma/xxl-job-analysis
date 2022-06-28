优化的3点

1.在我们netty-rpc client, 我们扫描所有被@RpcService注解的接口, 为这些接口
创建BeanDefinition, 指定其BeanClass是我们的factory类, 指定其接口的类型。

然后我们在这个factory类,把服务接口做动态代理，其InvocationHandler中的invoke函数就会发起rpc调用

那我们怎么引用服务者提供者接口呢

在Controller类中通过@Autowired注入服务者提供者接口即可。

但是这里需要优化的是, 我们发起rpc请求时, 构建request信息时, 传入的参数是服务提供者接口的className, 其实不太好,
实际上我们是要用classType。 这里需要提一下

在netty-rpc client和server中 我们都定义了InfoService, 但是只有server中有实现类。这里我偷懒了。

实际项目中 netty-rpc server中 要单独创建一个module用来暴露服务接口

netty-rpc client只需引用这个module就行

反观xxl-job的实现, 我觉得可以借鉴。

比如我们在Controller中 把服务接口打上`@XxlRpcReference`注解, 可以设置`timeout`,`version`,序列化算法等等等属性

然后`Spring`容器在这个服务接口依赖的bean实例化(postProcessAfterInstantiation)的时候, 
会为被@XxlRpcReference注解的服务接口字段，创建`XxlRpcReferenceBean`, 同时把引用赋值给这些服务接口。


```
public class XxlRpcSpringInvokerFactory extends InstantiationAwareBeanPostProcessorAdapter implements InitializingBean,DisposableBean, BeanFactoryAware {
    private Logger logger = LoggerFactory.getLogger(XxlRpcSpringInvokerFactory.class);

    // ---------------------- config ----------------------

    private Class<? extends ServiceRegistry> serviceRegistryClass;          // class.forname
    private Map<String, String> serviceRegistryParam;


    public void setServiceRegistryClass(Class<? extends ServiceRegistry> serviceRegistryClass) {
        this.serviceRegistryClass = serviceRegistryClass;
    }

    public void setServiceRegistryParam(Map<String, String> serviceRegistryParam) {
        this.serviceRegistryParam = serviceRegistryParam;
    }


    // ---------------------- util ----------------------

    private XxlRpcInvokerFactory xxlRpcInvokerFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        // start invoker factory
        xxlRpcInvokerFactory = new XxlRpcInvokerFactory(serviceRegistryClass, serviceRegistryParam);
        xxlRpcInvokerFactory.start();
    }

    @Override
    public boolean postProcessAfterInstantiation(final Object bean, final String beanName) throws BeansException {

        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                if (field.isAnnotationPresent(XxlRpcReference.class)) {
                    // valid
                    Class iface = field.getType();
                    if (!iface.isInterface()) {
                        throw new XxlRpcException("xxl-rpc, reference(XxlRpcReference) must be interface.");
                    }

                    XxlRpcReference rpcReference = field.getAnnotation(XxlRpcReference.class);

                    // init reference bean
                    XxlRpcReferenceBean referenceBean = new XxlRpcReferenceBean(
                            rpcReference.netType(),
                            rpcReference.serializer().getSerializer(),
                            rpcReference.callType(),
                            iface,
                            rpcReference.version(),
                            rpcReference.timeout(),
                            rpcReference.address(),
                            rpcReference.accessToken(),
                            null
                    );

                    Object serviceProxy = referenceBean.getObject();

                    // set bean
                    field.setAccessible(true);
                    field.set(bean, serviceProxy);

                    logger.info(">>>>>>>>>>> xxl-rpc, invoker factory init reference bean success. serviceKey = {}, bean.field = {}.{}",
                            XxlRpcProviderFactory.makeServiceKey(iface.getName(), rpcReference.version()), beanName, field.getName());
                }
            }
        });

        return super.postProcessAfterInstantiation(bean, beanName);
    }


    @Override
    public void destroy() throws Exception {

        // stop invoker factory
        xxlRpcInvokerFactory.stop();
    }

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
```

```
public class XxlRpcReferenceBean {
	private static final Logger logger = LoggerFactory.getLogger(XxlRpcReferenceBean.class);
	// [tips01: save 30ms/100invoke. why why why??? with this logger, it can save lots of time.]


	// ---------------------- config ----------------------

	private NetEnum netType;
	private Serializer serializer;
	private CallType callType;

	private Class<?> iface;
	private String version;

	private long timeout;

	private String address;
	private String accessToken;

	private XxlRpcInvokeCallback invokeCallback;

	public XxlRpcReferenceBean(NetEnum netType,
							   Serializer serializer,
							   CallType callType,
							   Class<?> iface,
							   String version,
							   long timeout,
							   String address,
							   String accessToken,
							   XxlRpcInvokeCallback invokeCallback
	) {

		this.netType = netType;
		this.serializer = serializer;
		this.callType = callType;
		this.iface = iface;
		this.version = version;
		this.timeout = timeout;
		this.address = address;
		this.accessToken = accessToken;
		this.invokeCallback = invokeCallback;

		// init Client
		initClient();
	}

	// get
	public Serializer getSerializer() {
		return serializer;
	}
	public long getTimeout() {
		return timeout;
	}

	// ---------------------- initClient ----------------------

	Client client = null;

	private void initClient() {
		try {
			client = netType.clientClass.newInstance();
			client.init(this);
		} catch (InstantiationException | IllegalAccessException e) {
			throw new XxlRpcException(e);
		}
	}


	// ---------------------- util ----------------------

	public String routeAddress(){
		String addressItem = address;
		if (addressItem!=null && addressItem.trim().length()>0) {
			return addressItem;
		}

		if (XxlRpcInvokerFactory.getServiceRegistry() != null) {
			String serviceKey = XxlRpcProviderFactory.makeServiceKey(iface.getName(), version);
			TreeSet<String> addressSet = XxlRpcInvokerFactory.getServiceRegistry().discovery(serviceKey);
			if (addressSet.size() > 0) {
				addressItem = new ArrayList<String>(addressSet).get(new Random().nextInt(addressSet.size()));
			}
		}
		return addressItem;
	}

	public Object getObject() {
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


	public Class<?> getObjectType() {
		return iface;
	}

}
```

2.在我们netty-rpc server端, 扫描@RpcService维护到一个map里面, key是接口类型,value是实现类。(服务提供者类)
  当消费者发起rpc调用时, 需要调用服务提供者实现类的method时, 就减少了反射服务提供者实现类的性能开销。
  这里是不是可以把服务实现类的所有method都可以缓存起来? 
  我觉得是不行的, rpc qps多了起来的话, 这些反射所影响的开销实际上很小。
这里的服务提供者实现类 扫描注册维护的方法, 和xxl-rpc provider的实现差不多

```
public class XxlRpcSpringProviderFactory extends XxlRpcProviderFactory implements ApplicationContextAware, InitializingBean,DisposableBean {

    // ---------------------- config ----------------------

    private String netType = NetEnum.JETTY.name();
    private String serialize = Serializer.SerializeEnum.HESSIAN.name();

    private String ip = IpUtil.getIp();		        // for registry
    private int port = 7080;       			        // default port
    private String accessToken;

    private Class<? extends ServiceRegistry> serviceRegistryClass;                          // class.forname
    private Map<String, String> serviceRegistryParam;


    // set
    public void setNetType(String netType) {
        this.netType = netType;
    }

    public void setSerialize(String serialize) {
        this.serialize = serialize;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setServiceRegistryClass(Class<? extends ServiceRegistry> serviceRegistryClass) {
        this.serviceRegistryClass = serviceRegistryClass;
    }

    public void setServiceRegistryParam(Map<String, String> serviceRegistryParam) {
        this.serviceRegistryParam = serviceRegistryParam;
    }


    // util
    private void prepareConfig(){

        // prepare config
        NetEnum netTypeEnum = NetEnum.autoMatch(netType, null);
        Serializer.SerializeEnum serializeEnum = Serializer.SerializeEnum.match(serialize, null);
        Serializer serializer = serializeEnum!=null?serializeEnum.getSerializer():null;

        if (port <= 0) {
            throw new XxlRpcException("xxl-rpc provider port["+ port +"] is unvalid.");
        }
        if (NetUtil.isPortUsed(port)) {
            throw new XxlRpcException("xxl-rpc provider port["+ port +"] is used.");
        }

        // init config
        super.initConfig(netTypeEnum, serializer, ip, port, accessToken, serviceRegistryClass, serviceRegistryParam);
    }


    // ---------------------- util ----------------------

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(XxlRpcService.class);
        if (serviceBeanMap!=null && serviceBeanMap.size()>0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                // valid
                if (serviceBean.getClass().getInterfaces().length ==0) {
                    throw new XxlRpcException("xxl-rpc, service(XxlRpcService) must inherit interface.");
                }
                // add service
                XxlRpcService xxlRpcService = serviceBean.getClass().getAnnotation(XxlRpcService.class);

                String iface = serviceBean.getClass().getInterfaces()[0].getName();
                String version = xxlRpcService.version();

                super.addService(iface, version, serviceBean);
            }
        }

        // TODO，addServices by api + prop

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.prepareConfig();
        super.start();
    }

    @Override
    public void destroy() throws Exception {
        super.stop();
    }

}

```
3.Grovvy中注入Spring容器中的属性 GET到了
```
public class SpringGlueFactory extends GlueFactory {
    private static Logger logger = LoggerFactory.getLogger(SpringGlueFactory.class);


    /**
     * inject action of spring
     * @param instance
     */
    @Override
    public void injectService(Object instance){
        if (instance==null) {
            return;
        }

        if (XxlJobSpringExecutor.getApplicationContext() == null) {
            return;
        }

        Field[] fields = instance.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object fieldBean = null;
            // with bean-id, bean could be found by both @Resource and @Autowired, or bean could only be found by @Autowired

            if (AnnotationUtils.getAnnotation(field, Resource.class) != null) {
                try {
                    Resource resource = AnnotationUtils.getAnnotation(field, Resource.class);
                    if (resource.name()!=null && resource.name().length()>0){
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(resource.name());
                    } else {
                        fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getName());
                    }
                } catch (Exception e) {
                }
                if (fieldBean==null ) {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            } else if (AnnotationUtils.getAnnotation(field, Autowired.class) != null) {
                Qualifier qualifier = AnnotationUtils.getAnnotation(field, Qualifier.class);
                if (qualifier!=null && qualifier.value()!=null && qualifier.value().length()>0) {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(qualifier.value());
                } else {
                    fieldBean = XxlJobSpringExecutor.getApplicationContext().getBean(field.getType());
                }
            }

            if (fieldBean!=null) {
                field.setAccessible(true);
                try {
                    field.set(instance, fieldBean);
                } catch (IllegalArgumentException e) {
                    logger.error(e.getMessage(), e);
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

}

```


4.Shiro通过实现DestructionAwareBeanPostProcessor 完成对Bean生命周期的掌握
异曲同工之妙的还有ApplicationListenerDetector等等等

我觉得xxl-job和我的netty-rpc demo 可以用这个更加优雅的方式
```
public class LifecycleBeanPostProcessor implements DestructionAwareBeanPostProcessor, PriorityOrdered {

    /**
     * Private internal class log instance.
     */
    private static final Logger log = LoggerFactory.getLogger(LifecycleBeanPostProcessor.class);

    /**
     * Order value of this BeanPostProcessor.
     */
    private int order;

    /**
     * Default Constructor.
     */
    public LifecycleBeanPostProcessor() {
        this(LOWEST_PRECEDENCE);
    }

    /**
     * Constructor with definable {@link #getOrder() order value}.
     *
     * @param order order value of this BeanPostProcessor.
     */
    public LifecycleBeanPostProcessor(int order) {
        this.order = order;
    }

    /**
     * Calls the <tt>init()</tt> methods on the bean if it implements {@link org.apache.shiro.util.Initializable}
     *
     * @param object the object being initialized.
     * @param name   the name of the bean being initialized.
     * @return the initialized bean.
     * @throws BeansException if any exception is thrown during initialization.
     */
    public Object postProcessBeforeInitialization(Object object, String name) throws BeansException {
        if (object instanceof Initializable) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Initializing bean [" + name + "]...");
                }

                ((Initializable) object).init();
            } catch (Exception e) {
                throw new FatalBeanException("Error initializing bean [" + name + "]", e);
            }
        }
        return object;
    }


    /**
     * Does nothing - merely returns the object argument immediately.
     */
    public Object postProcessAfterInitialization(Object object, String name) throws BeansException {
        // Does nothing after initialization
        return object;
    }


    /**
     * Calls the <tt>destroy()</tt> methods on the bean if it implements {@link org.apache.shiro.util.Destroyable}
     *
     * @param object the object being initialized.
     * @param name   the name of the bean being initialized.
     * @throws BeansException if any exception is thrown during initialization.
     */
    public void postProcessBeforeDestruction(Object object, String name) throws BeansException {
        if (object instanceof Destroyable) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("Destroying bean [" + name + "]...");
                }

                ((Destroyable) object).destroy();
            } catch (Exception e) {
                throw new FatalBeanException("Error destroying bean [" + name + "]", e);
            }
        }
    }

    /**
     * Order value of this BeanPostProcessor.
     *
     * @return order value.
     */
    public int getOrder() {
        // LifecycleBeanPostProcessor needs Order. See https://issues.apache.org/jira/browse/SHIRO-222
        return order;
    }

    /**
     * Return true only if <code>bean</code> implements Destroyable.
     * @param bean bean to check if requires destruction.
     * @return true only if <code>bean</code> implements Destroyable.
     * @since 1.4
     */
    @SuppressWarnings("unused")
    public boolean requiresDestruction(Object bean) {
        return (bean instanceof Destroyable);
    }
}
```
