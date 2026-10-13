use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

use jni::objects::{JClass, JIntArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong, jobjectArray, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::anim::{Animation, AnimationError};

struct JavaError {
    class: &'static str,
    message: String,
}

impl JavaError {
    fn bad_argument(message: impl Into<String>) -> Self {
        Self {
            class: "java/lang/IllegalArgumentException",
            message: message.into(),
        }
    }

    fn state(message: impl Into<String>) -> Self {
        Self {
            class: "java/lang/IllegalStateException",
            message: message.into(),
        }
    }

    fn from_animation(error: AnimationError) -> Self {
        if error.is_bad_argument() {
            Self::bad_argument(error.message())
        } else {
            Self::state(error.message())
        }
    }
}

fn boundary<'local, T>(
    env: &mut JNIEnv<'local>,
    fallback: T,
    operation: impl FnOnce(&mut JNIEnv<'local>) -> Result<T, JavaError>,
) -> T {
    match catch_unwind(AssertUnwindSafe(|| operation(env))) {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => {
            let _ = env.throw_new(error.class, error.message);
            fallback
        }
        Err(payload) => {
            let message = if let Some(message) = payload.downcast_ref::<String>() {
                message.clone()
            } else if let Some(message) = payload.downcast_ref::<&str>() {
                (*message).to_owned()
            } else {
                "panic in ttfx native code".to_owned()
            };
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            fallback
        }
    }
}

fn jni_error(error: jni::errors::Error) -> JavaError {
    JavaError::bad_argument(error.to_string())
}

fn animation_from_handle(handle: jlong) -> Result<&'static mut Animation, JavaError> {
    if handle == 0 {
        return Err(JavaError::state("animation is closed"));
    }
    // Handles are created and destroyed by TtfxNative; Kotlin clears them on close.
    Ok(unsafe { &mut *(handle as *mut Animation) })
}

#[no_mangle]
pub extern "system" fn Java_dev_omalauncher_ttfx_TtfxNative_create<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JString<'local>,
    args: JObjectArray<'local>,
    cols: jint,
    rows: jint,
    seed: jlong,
    has_seed: jboolean,
) -> jlong {
    boundary(&mut env, 0, |env| {
        if cols <= 0 || cols > i32::from(u16::MAX) || rows <= 0 || rows > i32::from(u16::MAX) {
            return Err(JavaError::bad_argument("canvas dimensions must be between 1 and 65535"));
        }
        let input: String = env.get_string(&input).map_err(jni_error)?.into();
        let length = env.get_array_length(&args).map_err(jni_error)?;
        let mut parsed_args: Vec<String> = Vec::with_capacity(length as usize);
        for index in 0..length {
            let value = env.get_object_array_element(&args, index).map_err(jni_error)?;
            let value: JString<'_> = value.into();
            parsed_args.push(env.get_string(&value).map_err(jni_error)?.into());
        }
        let animation = Animation::new_for_jni(
            &input,
            &parsed_args,
            cols as u16,
            rows as u16,
            (has_seed != 0).then_some(seed as u64),
        )
        .map_err(JavaError::from_animation)?;
        Ok(Box::into_raw(Box::new(animation)) as jlong)
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_omalauncher_ttfx_TtfxNative_next<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    glyphs: JIntArray<'local>,
    colors: JIntArray<'local>,
) -> jboolean {
    boundary(&mut env, JNI_FALSE, |env| {
        let animation = animation_from_handle(handle)?;
        let length = animation.glyphs.len() as jint;
        if env.get_array_length(&glyphs).map_err(jni_error)? < length
            || env.get_array_length(&colors).map_err(jni_error)? < length
        {
            return Err(JavaError::bad_argument("frame arrays are smaller than the animation grid"));
        }
        let running = animation.next_frame();
        let glyphs_as_ints: Vec<jint> = animation.glyphs.iter().map(|glyph| *glyph as jint).collect();
        let colors_as_ints: Vec<jint> = animation.colors.iter().map(|color| *color as jint).collect();
        env.set_int_array_region(&glyphs, 0, &glyphs_as_ints).map_err(jni_error)?;
        env.set_int_array_region(&colors, 0, &colors_as_ints).map_err(jni_error)?;
        Ok(if running { JNI_TRUE } else { JNI_FALSE })
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_omalauncher_ttfx_TtfxNative_effect<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jstring {
    boundary(&mut env, ptr::null_mut(), |env| {
        let animation = animation_from_handle(handle)?;
        env.new_string(animation.effect()).map(|value| value.into_raw()).map_err(jni_error)
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_omalauncher_ttfx_TtfxNative_destroy<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    boundary(&mut env, (), |_| {
        if handle != 0 {
            drop(unsafe { Box::from_raw(handle as *mut Animation) });
        }
        Ok(())
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_omalauncher_ttfx_TtfxNative_effects<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jobjectArray {
    boundary(&mut env, ptr::null_mut(), |env| {
        let names = crate::anim::effect_names();
        let array = env
            .new_object_array(names.len() as jint, "java/lang/String", JObject::null())
            .map_err(jni_error)?;
        for (index, name) in names.iter().enumerate() {
            let value = env.new_string(name).map_err(jni_error)?;
            env.set_object_array_element(&array, index as jint, value).map_err(jni_error)?;
        }
        Ok(array.into_raw())
    })
}
