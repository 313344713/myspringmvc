package com.study.mvcframework.servlet;

import com.study.demo.action.DemoAction;
import com.study.mvcframework.annotation.*;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {

    private Properties p = new Properties();

    private List<String> classNames = new ArrayList<String>();

    private Map<String, Object> ioc = new HashMap<String, Object>();

    //保存所有的Url和方法的映射关系
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void init(ServletConfig config) throws ServletException {

        System.out.println("=============== init... ===================");
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2、根据加载的配置文件扫描相关的类
        doScanner(p.getProperty("scanPackage"));

        //3、初始化IOC容器
        doInstance();

        //4、DI
        doAutowired();

        //5、初始化 handlerMapping
        initHandlerMapping();

        //6、调用
    }


    /**
     * 从 ioc 容器中取出所有带 MyController 注解的bean
     * 获取 带 MyRequestMapping 注解的 方法
     * 添加到 handler 集合中
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (clazz.isAnnotationPresent(MyController.class)) {
                String controllerUrl = "";
                //获取Controller的url配置
                if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                    MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                    controllerUrl = requestMapping.value();
                }
                Method[] methods = clazz.getMethods();
                for ( Method m : methods) {
                    if (m.isAnnotationPresent(MyRequestMapping.class)) {
                        MyRequestMapping myRequestMapping = m.getAnnotation(MyRequestMapping.class);
                        String url = ("/" + controllerUrl + "/" + myRequestMapping.value()).replaceAll("/+","/");
                        handlerMapping.add(new Handler(url, entry.getValue(), m));
                    }
                }
            }
        }
    }

    /**
     * 遍历 IOC 容器， 把有 MyAutowired 注解的属性 初始化
     */
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        } else {
            for ( Map.Entry<String, Object> entry : ioc.entrySet()) {
                //获取所有的属性，包含private属性
                Field[] fields = entry.getValue().getClass().getDeclaredFields();
                //判断属性是否有MyAutowired注解
                for (Field f : fields) {
                    if (f.isAnnotationPresent(MyAutowired.class)) {
                        //判断是否有设置 自定义bean
                        MyAutowired myAutowired = f.getAnnotation(MyAutowired.class);
                        String beanName = myAutowired.value();
                        if (StringUtils.isEmpty(myAutowired.value())) {
                            beanName = f.getType().getName();
                        }
                        //设置私有属性的访问权限
                        f.setAccessible(true);
                        try {
                            //给对应属性初始化
                            f.set(entry.getValue(), ioc.get(beanName));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    }

    /**
     * 遍历 classNames  ，把有注解的类 放入 IOC容器
     */
    private void doInstance() {
        if (classNames.size() == 0) {
            return;
        }
        try {
            for (String bean : classNames) {
                Class<?> clazz = Class.forName(bean);
                if (clazz.isAnnotationPresent(MyController.class)) {
                    //首字母小写
                    ioc.put(lowerFirst(clazz.getSimpleName()), clazz.newInstance());
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    MyService myService = clazz.getAnnotation(MyService.class);
                    String beanName = myService.value();
                    //取自定义name
                    if (StringUtils.isNotEmpty(beanName)) {
                        ioc.put(beanName, clazz.newInstance());
                    } else {
                        Class<?>[] interfaces = clazz.getInterfaces();
                        for (Class<?> c : interfaces) {
                            // 注意是 clazz.newInstance
                            ioc.put(c.getName(), clazz.newInstance());
                        }
                    }

                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 遍历 scanPackage 下所有的类，并放入 list 容器中（全路径）
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        //将包名转换成文件路径
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replace(".", "/"));
        File dir = new File(url.getFile());
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                doScanner( scanPackage + "." + f.getName());
            } else {
                classNames.add(scanPackage + "." + f.getName().replace(".class", ""));
            }
        }
    }

    /**
     * 加载配置文件
     * @param location
     */
    private void doLoadConfig(String location) {
        InputStream in = null;
        try {
            in = this.getClass().getClassLoader().getResourceAsStream(location);
            p.load(in);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp); //开始始匹配到对应的方方法
        } catch(Exception e) {
            //如果匹配过程出现异常，将异常信息打印出去
            resp.getWriter().write("500 Exception,Details:\r\n"
                    + Arrays.toString(e.getStackTrace())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", "\r\n"));
        }
    }

    /**
     * 先判断 请求 url 是否存在 handlerMapping 中
     *
     * @param req
     * @param resp
     * @throws Exception
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        try {
            Handler handler = getHandler(req);
            if(handler == null){
                //如果没有匹配上，返回404错误
                resp.getWriter().write("404 Not Found");
                return;
            }
            //获取方法的参数列表
            Class<?> [] paramTypes = handler.method.getParameterTypes();

            //保存所有需要自动赋值的参数值，用于反射调用
            Object [] paramValues = new Object[paramTypes.length];

            //请求参数
            Map<String,String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue())
                        .replaceAll("\\[|\\]", "")
                        .replaceAll(",\\s", ",");
                //如果找到匹配的对象，则开始填充参数值
                if (handler.paramIndexMapping.containsKey(param.getKey())) {
                    int index = handler.paramIndexMapping.get(param.getKey());
                    paramValues[index] = convert(paramTypes[index],value);
                }

            }

            //设置方法中的request和response对象
            int reqIndex = handler.paramIndexMapping.get("arg0");
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get("arg1");
            paramValues[respIndex] = resp;

            handler.method.invoke(handler.controller, paramValues);
        } catch(Exception e){
            e.printStackTrace();
            throw e;
        }
    }


    /**
     * 根据 handlerMapping 中 url 匹配请求 url
     * @param req
     * @return
     * @throws Exception
     */
    private Handler getHandler(HttpServletRequest req) throws Exception{
        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        for (Handler handler : handlerMapping) {
            try{
                if (url .equals(handler.url)) {
                    return handler;
                }
            }catch(Exception e){
                throw e;
            }
        }
        return null;
    }

    //url传过来的参数都是String类型的，HTTP是基于字符串协议
    //只需要把String转换为任意类型就好
    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        //如果还有double或者其他类型，继续加if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }


    public static void main(String[] args) {
        String s = ".class";
        System.out.println(s.replace(".", "/"));
        String s1 = "aClass";
        System.out.println(lowerFirst(s1));
        try {
            Method method = DemoAction.class.getMethod("add",HttpServletRequest.class,HttpServletResponse.class,Integer.class,Integer.class, String.class);
            Handler h = new MyDispatcherServlet().new Handler("11","aa", method);

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

    }

    private static String lowerFirst(String str) {
        char[] c = str.toCharArray();
        if (c[0] < 97) {
            c[0] += 32;
        }
        return String.valueOf(c);
    }

    /**
     * 记录 Controller 中的 RequestMapping 和 Method 的对应关系
     */
    private class Handler {

        private Object controller;

        private Method method;

        private String url;

        //参数顺序
        private Map<String,Integer> paramIndexMapping;

        /**
         * 构造一个 Handler 基本的参数
         * @param controller
         * @param method
         */
        protected Handler (String url, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.url = url;

            paramIndexMapping = new HashMap<String,Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {

            //jdk1.8支持，Setting-**-java Compiler 设置 -parameters （带参数名编译，否则会自动转换成 arg0 arg1）
            Parameter[] p = method.getParameters();
            for (int i = 0 ; i < p.length; i++) {
                Parameter parameter = p [i];
                MyRequestParam myRequestParam = parameter.getAnnotation(MyRequestParam.class);
                if (myRequestParam != null) {
                    String value = myRequestParam.value();
                    if (StringUtils.isNotEmpty(value)) {
                        paramIndexMapping.put(value, i);
                        System.out.println(value + "======" + i);
                        continue;
                    }
                }
                paramIndexMapping.put(parameter.getName(), i);
                System.out.println(parameter.getName() + "------" + i  );

            }


            /*//提取方法中加了注解的参数
            Annotation[] [] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length ; i ++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof MyRequestParam) {
                        String paramName = ((MyRequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i + 2);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?> [] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length ; i ++) {
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(),i);
                }
            }*/
        }

    }
}
