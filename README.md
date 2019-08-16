# 流程梳理
1、加载配置
2、根据配置文件中扫描路径 加载路径下的所有类  放入 List 集合中
3、初始化 IOC map容器（遍历步骤2中List集合，取出带有 @Controller、@Service、@Component 注解的bean）
4、依赖注入（IOC容器中，属性带有 @Autowired 注解的初始化）
5、初始化 url 跟 method 映射关系 List<Handler> handlerMapping
6、根据用户输入的 url 到 handlerMapping 匹配对应的 Handler，通过反射执行 method
