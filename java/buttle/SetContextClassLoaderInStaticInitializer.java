package buttle;

public class SetContextClassLoaderInStaticInitializer {

	// https://docs.jboss.org/author/display/AS71/Class+Loading+in+AS7

	// https://docs.jboss.org/author/display/MODULES/Home

	static {
		Thread.currentThread().setContextClassLoader(SetContextClassLoaderInStaticInitializer.class.getClassLoader());
	}

}
