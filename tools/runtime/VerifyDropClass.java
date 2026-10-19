/** Offline class resolution only; never constructs or enables a Bukkit plugin. */
class VerifyDropClass {
    public static void main(String[] args) throws Exception {
        Class<?> type=Class.forName("cn.genlite.platform.GeneratorDropSpace",false,VerifyDropClass.class.getClassLoader());
        var method=type.getDeclaredMethod("available",org.bukkit.Location.class,String.class,int.class);
        if(method.getReturnType()!=boolean.class) throw new IllegalStateException("Incorrect drop space signature");
        System.out.println("GeneratorDropSpace resolves from "+type.getProtectionDomain().getCodeSource().getLocation());
    }
}
