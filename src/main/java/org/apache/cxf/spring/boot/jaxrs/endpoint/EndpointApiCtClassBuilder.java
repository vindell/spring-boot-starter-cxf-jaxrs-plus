package org.apache.cxf.spring.boot.jaxrs.endpoint;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebParam.Mode;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.jws.WebResult;
import javax.jws.WebService;

import org.apache.commons.lang3.builder.Builder;
import org.apache.cxf.spring.boot.jaxrs.annotation.WebBound;
import org.springframework.util.StringUtils;

import com.github.vindell.javassist.utils.JavassistUtils;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.ParameterAnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.StringMemberValue;

/**
 * 
 * 动态构建ws接口
 * <p>http://www.cnblogs.com/sunfie/p/5154246.html</p>
 * <p>http://blog.csdn.net/youaremoon/article/details/50766972</p>
 * <p>https://blog.csdn.net/tscyds/article/details/78415172</p>
 * <p>https://my.oschina.net/GameKing/blog/794580</p>
 * <p>http://wsmajunfeng.iteye.com/blog/1912983</p>
 */
public class EndpointApiCtClassBuilder implements Builder<CtClass> {
	
	// 构建动态类
	private ClassPool pool = null;
	private CtClass ctclass  = null;
	private ClassFile ccFile = null;
	//private Loader loader = new Loader(pool);
	
	public EndpointApiCtClassBuilder(final String classname) throws CannotCompileException, NotFoundException  {
		this(JavassistUtils.getDefaultPool(), classname);
	}
	
	public EndpointApiCtClassBuilder(final ClassPool pool, final String classname) throws CannotCompileException, NotFoundException {
		
		this.pool = pool;
		this.ctclass = this.pool.getOrNull(classname);
		if( null == this.ctclass) {
			this.ctclass = this.pool.makeInterface(classname);
		}
		
		/* 指定 Cloneable 作为动态接口的父类 */
		CtClass superclass = pool.get(Cloneable.class.getName());
		ctclass.setSuperclass(superclass);
		
		// 当 ClassPool.doPruning=true的时候，Javassist 在CtClass object被冻结时，会释放存储在ClassPool对应的数据。这样做可以减少javassist的内存消耗。默认情况ClassPool.doPruning=false。
		this.ctclass.stopPruning(true);
		this.ccFile = this.ctclass.getClassFile();
	}
	
	/**
	 * @description ： 给动态类添加 @WebService 注解
	 * @param name： 此属性的值包含XML Web Service的名称。在默认情况下，该值是实现XML Web Service的类的名称，wsdl:portType 的名称。缺省值为 Java 类或接口的非限定名称。（字符串）
	 * @param targetNamespace：指定你想要的名称空间，默认是使用接口实现类的包名的反缀（字符串）
	 * @return
	 */
	public EndpointApiCtClassBuilder annotationForType(final String path, final String... mediaTypes) {

		ConstPool constPool = this.ccFile.getConstPool();
		 
		// 添加类注解 @Path
		AnnotationsAttribute classAttr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
		
		Annotation pathAnnt = new Annotation(Path.class.getName(), constPool);
		pathAnnt.addMemberValue("value", new StringMemberValue(path, constPool));
		classAttr.addAnnotation(pathAnnt);
		
		// 添加类注解 @Produces
		Annotation producesAnnt = new Annotation(Produces.class.getName(), constPool);
		producesAnnt.addMemberValue("value", new ArrayMemberValue(mediaTypes, constPool));
		classAttr.addAnnotation(producesAnnt);
		
		ccFile.addAttribute(classAttr);
		
		return this;
	}
	
	/**
	 * 通过给动态类增加 <code>@WebBound</code>注解实现，数据的绑定
	 * TODO
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param uid
	 * @param json
	 * @return
	 */
	public EndpointApiCtClassBuilder bindDataForType(final String uid, final String json) {

		ConstPool constPool = this.ccFile.getConstPool();
		// 添加类注解 @WebBound
		AnnotationsAttribute classAttr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
		Annotation bound = new Annotation(WebBound.class.getName(), constPool);
		bound.addMemberValue("uid", new StringMemberValue(uid, constPool));
		bound.addMemberValue("json", new StringMemberValue(json, constPool));
		classAttr.addAnnotation(bound);
		ccFile.addAttribute(classAttr);
		
		return this;
	}
	
	/**
     * Compiles the given source code and creates a field.
     * Examples of the source code are:
     * 
     * <pre>
     * "public String name;"
     * "public int k = 3;"</pre>
     *
     * <p>Note that the source code ends with <code>';'</code>
     * (semicolon).
     *
     * @param src               the source text.
     */
	public <T> EndpointApiCtClassBuilder makeField(final String src) throws CannotCompileException {
		//创建属性
        ctclass.addField(CtField.make(src, ctclass));
		return this;
	}
	
	public <T> EndpointApiCtClassBuilder newField(final Class<T> fieldClass, final String fieldName, final String fieldValue) throws CannotCompileException, NotFoundException {
		
		// 检查字段是否已经定义
		if(JavassistUtils.hasField(ctclass, fieldName)) {
			return this;
		}
		
		/** 添加属性字段 */
		CtField field = new CtField(this.pool.get(fieldClass.getName()), fieldName, ctclass);
        field.setModifiers(Modifier.PRIVATE);

        //新增Field
        ctclass.addField(field, "\"" + fieldValue + "\"");
        
		return this;
	}
	
	public <T> EndpointApiCtClassBuilder removeField(final String fieldName) throws NotFoundException {
		
		// 检查字段是否已经定义
		if(!JavassistUtils.hasField(ctclass, fieldName)) {
			return this;
		}
		
		ctclass.removeField(ctclass.getDeclaredField(fieldName));
		
		return this;
	}
	
	/**
	 * 数据绑定对象，用于通过<code>@WebBound</code>注解实现与方法相关数据的绑定
	 */
	public static class CtWebBound {
	    
	    public CtWebBound(String uid) {
	    	this.uid = uid;
		}
	    
		public CtWebBound(String uid, String json) {
			this.uid = uid;
			this.json = json;
		}

		/**
		 * 1、uid：某个数据主键，可用于传输主键ID在实现对象中进行数据提取
		 */
		private String uid = "";

		/**
		 * 2、json：绑定的数据对象JSON格式，为了方便，这里采用json进行数据传输
		 */
		private String json = "";

		public String getUid() {
			return uid;
		}

		public void setUid(String uid) {
			this.uid = uid;
		}

		public String getJson() {
			return json;
		}

		public void setJson(String json) {
			this.json = json;
		}

	}
	
	/**
	 * 注释表示作为一项 Web Service 操作的方法，将此注释应用于客户机或服务器服务端点接口（SEI）上的方法，或者应用于 JavaBeans 端点的服务器端点实现类。
	 * 要点： 仅支持在使用 @WebService 注释来注释的类上使用 @WebMethod 注释
	 * https://www.cnblogs.com/zhao-shan/p/5515174.html
	 */
	public static class CtWebMethod {
	    
	    public CtWebMethod() {
		}
	    
	    public CtWebMethod(String operationName) {
			this.operationName = operationName;
		}
	    
		public CtWebMethod(String operationName, String action, boolean exclude) {
			this.operationName = operationName;
			this.action = action;
			this.exclude = exclude;
		}

		/**
		 * 1、operationName：指定与此方法相匹配的wsdl:operation 的名称。缺省值为 Java 方法的名称。（字符串）
		 */
		private String operationName = "";

		/**
		 * 2、action：定义此操作的行为。对于 SOAP 绑定，此值将确定 SOAPAction 头的值。缺省值为 Java 方法的名称。（字符串）
		 */
		private String action = "";

		/**
		 * 3、exclude：指定是否从 Web Service 中排除某一方法。缺省值为 false。（布尔值）  
		 */
		private boolean exclude = false;

		public String getOperationName() {
			return operationName;
		}

		public void setOperationName(String operationName) {
			this.operationName = operationName;
		}

		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public boolean isExclude() {
			return exclude;
		}

		public void setExclude(boolean exclude) {
			this.exclude = exclude;
		}

	}
	
	
	/**
	 * 注释用于定制从单个参数至 Web Service 消息部件和 XML 元素的映射。将此注释应用于客户机或服务器服务端点接口（SEI）上的方法，或者应用于 JavaBeans 端点的服务器端点实现类。
	 * https://www.cnblogs.com/zhao-shan/p/5515174.html
	 */
	public static class CtWebParam<T> {

		public CtWebParam(Class<T> type, String name) {
			this.type = type;
			this.name = name;
		}
		
		public CtWebParam(Class<T> type, String name,boolean header) {
			this.type = type;
			this.name = name;
			this.header = header;
		}
		
		public CtWebParam(Class<T> type, String name,Mode mode) {
			this.type = type;
			this.name = name;
			this.mode = mode;
		}
		
		public CtWebParam(Class<T> type, String name,Mode mode, boolean header) {
			this.type = type;
			this.name = name;
			this.mode = mode;
			this.header = header;
		}
		
		public CtWebParam(Class<T> type, String name, String partName, String targetNamespace, Mode mode,
				boolean header) {
			this.type = type;
			this.name = name;
			this.partName = partName;
			this.targetNamespace = targetNamespace;
			this.mode = mode;
			this.header = header;
		}

		/**
		 * 参数对象类型
		 */
		private Class<T> type;
		/**
		 * 1、name ：参数的名称。如果操作是远程过程调用（RPC）类型并且未指定partName 属性，那么这是用于表示参数的 wsdl:part 属性的名称。
		 * 如果操作是文档类型或者参数映射至某个头，那么 -name 是用于表示该参数的 XML 元素的局部名称。如果操作是文档类型、 参数类型为 BARE
		 * 并且方式为 OUT 或 INOUT，那么必须指定此属性。（字符串）
		 */
		private String name = "";
		/**
		 * 2、partName：定义用于表示此参数的 wsdl:part属性的名称。仅当操作类型为 RPC 或者操作是文档类型并且参数类型为BARE
		 * 时才使用此参数。（字符串）
		 */
		private String partName = "";
		/**
		 * 3、targetNamespace：指定参数的 XML 元素的 XML 名称空间。当属性映射至 XML 元素时，仅应用于文档绑定。缺省值为 Web
		 * Service 的 targetNamespace。（字符串）
		 */
		private String targetNamespace = "";
		/**
		 * 4、mode：此值表示此方法的参数流的方向。有效值为 IN、INOUT 和 OUT。（字符串）
		 */
		private javax.jws.WebParam.Mode mode = javax.jws.WebParam.Mode.IN;
		/**
		 * 5、header：指定参数是在消息头还是消息体中。缺省值为 false。（布尔值）
		 */
		private boolean header = false;

		public Class<T> getType() {
			return type;
		}

		public void setType(Class<T> type) {
			this.type = type;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getPartName() {
			return partName;
		}

		public void setPartName(String partName) {
			this.partName = partName;
		}

		public String getTargetNamespace() {
			return targetNamespace;
		}

		public void setTargetNamespace(String targetNamespace) {
			this.targetNamespace = targetNamespace;
		}

		public javax.jws.WebParam.Mode getMode() {
			return mode;
		}

		public void setMode(javax.jws.WebParam.Mode mode) {
			this.mode = mode;
		}

		public boolean isHeader() {
			return header;
		}

		public void setHeader(boolean header) {
			this.header = header;
		}
		
	}
	
	/**
	 * 注释用于定制从返回值至 WSDL 部件或 XML 元素的映射。将此注释应用于客户机或服务器服务端点接口（SEI）上的方法，或者应用于 JavaBeans 端点的服务器端点实现类。
	 * https://www.cnblogs.com/zhao-shan/p/5515174.html
	 */
	public static class CtWebResult<T> {

		public CtWebResult(Class<T> rtClass) {
			this.rtClass = rtClass;
		}
		
		public CtWebResult(Class<T> rtClass, String name, String targetNamespace, boolean header, String partName) {
			this.rtClass = rtClass;
			this.name = name;
			this.targetNamespace = targetNamespace;
			this.header = header;
			this.partName = partName;
		}

		/**
		 * 返回结果对象类型
		 */
		private Class<T> rtClass;
		
		/**
		 * 1、name：当返回值列示在 WSDL 文件中并且在连接上的消息中找到该返回值时，指定该返回值的名称。对于 RPC 绑定，这是用于表示返回值的
		 * wsdl:part属性的名称。对于文档绑定，-name 参数是用于表示返回值的 XML 元素的局部名。对于 RPC 和 DOCUMENT/WRAPPED
		 * 绑定，缺省值为 return。对于 DOCUMENT/BARE 绑定，缺省值为方法名 + Response。（字符串）
		 */
		private String name = "";

		/**
		 * 2、targetNamespace：指定返回值的 XML 名称空间。仅当操作类型为 RPC 或者操作是文档类型并且参数类型为 BARE
		 * 时才使用此参数。（字符串）
		 */
		private String targetNamespace = "";

		/**
		 * 3、header：指定头中是否附带结果。缺省值为false。（布尔值）
		 */
		private boolean header = false;
		/**
		 * 4、partName：指定 RPC 或 DOCUMENT/BARE 操作的结果的部件名称。缺省值为@WebResult.name。（字符串）
		 */
		private String partName = "";

		public Class<T> getRtClass() {
			return rtClass;
		}

		public void setRtClass(Class<T> rtClass) {
			this.rtClass = rtClass;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getTargetNamespace() {
			return targetNamespace;
		}

		public void setTargetNamespace(String targetNamespace) {
			this.targetNamespace = targetNamespace;
		}

		public boolean isHeader() {
			return header;
		}

		public void setHeader(boolean header) {
			this.header = header;
		}

		public String getPartName() {
			return partName;
		}

		public void setPartName(String partName) {
			this.partName = partName;
		}

	}
	
	/**
	 * 
	 * 根据参数构造一个新的方法
	 * @param rtClass 		：方法返回类型
	 * @param methodName 	：方法名称
	 * @param params		： 参数信息
	 * @return
	 * @throws CannotCompileException
	 * @throws NotFoundException 
	 */
	public <T> EndpointApiCtClassBuilder abstractMethod(final Class<T> rtClass, final String methodName, CtWebParam<?>... params) throws CannotCompileException, NotFoundException {
		return this.abstractMethod(new CtWebResult<T>(rtClass), new CtWebMethod(methodName), null, params);
	}
	
	/**
	 * 
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @param rtClass 		：方法返回类型
	 * @param methodName 	：方法名称
	 * @param bound			：方法绑定数据信息
	 * @param params		： 参数信息
	 * @return
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	public <T> EndpointApiCtClassBuilder abstractMethod(final Class<T> rtClass, final String methodName, final CtWebBound bound, CtWebParam<?>... params) throws CannotCompileException, NotFoundException {
		return this.abstractMethod(new CtWebResult<T>(rtClass), new CtWebMethod(methodName), bound, params);
	}
	
	/**
	 * 
	 * 根据参数构造一个新的方法
	 * @param result ：返回结果信息
	 * @param method ：方法注释信息
	 * @param bound  ：方法绑定数据信息
	 * @param params ： 参数信息
	 * @return
	 * @throws CannotCompileException
	 * @throws NotFoundException 
	 */ 
	public <T> EndpointApiCtClassBuilder abstractMethod(final CtWebResult<T> result, final CtWebMethod method, final CtWebBound bound, CtWebParam<?>... params) throws CannotCompileException, NotFoundException {
	       
		ConstPool constPool = this.ccFile.getConstPool();
		
		// 创建抽象方法
		CtClass returnType = pool.get(result.getRtClass().getName());
		CtClass[] exceptions = new CtClass[] { pool.get("java.lang.Exception") };
		CtMethod ctMethod = null;
		// 参数模式定义
		Map<String, EnumMemberValue> modeMap = new HashMap<String, EnumMemberValue>();
		// 有参方法
		if(params != null && params.length > 0) {
			
			// 方法参数
			CtClass[] paramTypes = new CtClass[params.length];
			for(int i = 0;i < params.length; i++) {
				paramTypes[i] = this.pool.get(params[i].getType().getName());
				if(!modeMap.containsKey(params[i].getMode().name())) {
					
					EnumMemberValue modeEnum = new EnumMemberValue(constPool);
			        modeEnum.setType(Mode.class.getName());
			        modeEnum.setValue(params[i].getMode().name());
					
					modeMap.put(params[i].getMode().name(), modeEnum);
				}
			}
			
			// 构造抽象方法
			ctMethod = CtNewMethod.abstractMethod(returnType, method.getOperationName(), paramTypes , exceptions, ctclass);
			
		} 
		/**无参方法 */
		else {
			
			// 构造抽象方法
			ctMethod = CtNewMethod.abstractMethod(returnType, method.getOperationName(), null , exceptions, ctclass);
			
		}
		
        // 添加方法注解
        AnnotationsAttribute methodAttr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
       
        // 添加 @WebMethod 注解	        
        Annotation methodAnnot = new Annotation(WebMethod.class.getName(), constPool);
        methodAnnot.addMemberValue("operationName", new StringMemberValue(method.getOperationName(), constPool));
        if (StringUtils.hasText(method.getAction())) {
        	methodAnnot.addMemberValue("action", new StringMemberValue(method.getAction(), constPool));
        }
        methodAnnot.addMemberValue("exclude", new BooleanMemberValue(method.isExclude(), constPool));
        
        methodAttr.addAnnotation(methodAnnot);
        
        // 添加 @WebResult 注解
        if (StringUtils.hasText(result.getName())) {
        	
        	Annotation resultAnnot = new Annotation(WebResult.class.getName(), constPool);
	        resultAnnot.addMemberValue("name", new StringMemberValue(result.getName(), constPool));
	        if (StringUtils.hasText(result.getPartName())) {
	        	resultAnnot.addMemberValue("partName", new StringMemberValue(result.getPartName(), constPool));
	        }
	        if (StringUtils.hasText(result.getTargetNamespace())) {
	        	resultAnnot.addMemberValue("targetNamespace", new StringMemberValue(result.getTargetNamespace(), constPool));
	        }
	        resultAnnot.addMemberValue("header", new BooleanMemberValue(result.isHeader(), constPool));
	        
	        methodAttr.addAnnotation(resultAnnot);
	        
        }
        
        // 添加 @WebBound 注解
        if (bound != null) {
        	
        	Annotation resultBound = new Annotation(WebBound.class.getName(), constPool);
	        resultBound.addMemberValue("uid", new StringMemberValue(bound.getUid(), constPool));
	        if (StringUtils.hasText(bound.getJson())) {
	        	resultBound.addMemberValue("json", new StringMemberValue(bound.getJson(), constPool));
	        }
	        methodAttr.addAnnotation(resultBound);
	        
        }
        
        
        ctMethod.getMethodInfo().addAttribute(methodAttr);
        
        // 添加 @WebParam 参数注解
        if(params != null && params.length > 0) {
        	
        	ParameterAnnotationsAttribute parameterAtrribute = new ParameterAnnotationsAttribute(constPool, ParameterAnnotationsAttribute.visibleTag);
            Annotation[][] paramArrays = new Annotation[params.length][1];
            
            Annotation paramAnnot = null;
            for(int i = 0;i < params.length; i++) {
            	
            	paramAnnot = new Annotation(WebParam.class.getName(), constPool);
                paramAnnot.addMemberValue("name", new StringMemberValue(params[i].getName(), constPool));
                if (StringUtils.hasText(params[i].getPartName())) {
                	paramAnnot.addMemberValue("partName", new StringMemberValue(params[i].getPartName(), constPool));
        		}
                paramAnnot.addMemberValue("targetNamespace", new StringMemberValue(params[i].getTargetNamespace(), constPool));
                paramAnnot.addMemberValue("mode", modeMap.get(params[i].getMode().name()));
                if(params[i].isHeader()) {
                	 paramAnnot.addMemberValue("header", new BooleanMemberValue(true, constPool));
                }
                
                paramArrays[i][0] = paramAnnot;
                
            }
            
            parameterAtrribute.setAnnotations(paramArrays);
            ctMethod.getMethodInfo().addAttribute(parameterAtrribute);
            
        }
        
        //新增方法
        ctclass.addMethod(ctMethod);
        
        return this;
	}
	
	public <T> EndpointApiCtClassBuilder removeMethod(final String methodName, CtWebParam<?>... params) throws NotFoundException {
		
		// 有参方法
		if(params != null && params.length > 0) {
			
			// 方法参数
			CtClass[] paramTypes = new CtClass[params.length];
			for(int i = 0;i < params.length; i++) {
				paramTypes[i] = this.pool.get(params[i].getType().getName());
			}
			
			// 检查方法是否已经定义
			if(!JavassistUtils.hasMethod(ctclass, methodName, paramTypes)) {
				return this;
			}
			
			ctclass.removeMethod(ctclass.getDeclaredMethod(methodName, paramTypes));
			
		}
		else {
			
			// 检查方法是否已经定义
			if(!JavassistUtils.hasMethod(ctclass, methodName)) {
				return this;
			}
			
			ctclass.removeMethod(ctclass.getDeclaredMethod(methodName));
			
		}
		
		return this;
	}
	
	@Override
	public CtClass build() {
        return ctclass;
	}
	
	/**
	 * 
	 * javassist在加载类时会用Hashtable将类信息缓存到内存中，这样随着类的加载，内存会越来越大，甚至导致内存溢出。如果应用中要加载的类比较多，建议在使用完CtClass之后删除缓存
	 * @author 		： <a href="https://github.com/vindell">vindell</a>
	 * @return
	 * @throws CannotCompileException
	 */
	public Class<?> toClass() throws CannotCompileException {
        try {
        	// 通过类加载器加载该CtClass
			return ctclass.toClass();
		} finally {
			// 将该class从ClassPool中删除
			ctclass.detach();
		} 
	}
	
	@SuppressWarnings("unchecked")
	public Object toInstance(final InvocationHandler handler) throws CannotCompileException, NotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        try {
        	// 添加有参构造器，注入回调接口
			CtConstructor cc = new CtConstructor(new CtClass[] { pool.get(InvocationHandler.class.getName()) }, ctclass);
			cc.setBody("{super($1);}");
			ctclass.addConstructor(cc);
			// proxy.writeFile();
			// 通过类加载器加载该CtClass，并通过构造器初始化对象
			return ctclass.toClass().getConstructor(InvocationHandler.class).newInstance(handler);
		} finally {
			// 将该class从ClassPool中删除
			ctclass.detach();
		} 
	}

}