package net.minecraftforge.common;
public class ForgeConfigSpec { public static class ConfigValue<T> { public T v; public T get(){return v;} } 
  public static class BooleanValue extends ConfigValue<Boolean> {} public static class IntValue extends ConfigValue<Integer> {} public static class Builder {} }
