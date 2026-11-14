//! JNI bridge: the flatulence creature of the chimera engine, for the Pukpuk Android app.
use chimera::engine::Engine;
use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray, JFloatArray, JString};
use jni::sys::{jboolean, jdouble, jint, jlong, jstring};
use std::sync::Mutex;

struct State {
    engine: Engine,
    tele: chimera::creatures::voice::CreatureTelemetry,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn with<R>(f: impl FnOnce(&mut State) -> R) -> Option<R> {
    STATE.lock().ok()?.as_mut().map(f)
}

/// Create the engine playing `flatulence`; returns the creature description as JSON.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeInit(env: JNIEnv, _c: JClass, sample_rate: jdouble) -> jstring {
    let mut engine = Engine::new(sample_rate);
    let def = chimera::creatures::creature_by_id("flatulence").expect("flatulence creature");
    engine.set_creature(Some(def), &[]);
    let mut tele = engine.creature().unwrap().new_telemetry();
    engine.creature_telemetry(&mut tele);
    let info = serde_json::json!({
        "params": def.params.iter().map(|p| serde_json::json!({
            "key": p.key, "label": p.label, "min": p.min, "max": p.max, "default": p.default,
            "unit": p.unit, "log": p.log.unwrap_or(false), "toggle": p.toggle.unwrap_or(false),
        })).collect::<Vec<_>>(),
        "calls": def.calls.iter().map(|c| serde_json::json!({"id": c.id, "name": c.name, "hint": c.hint})).collect::<Vec<_>>(),
        "sources": tele.source.iter().map(|(k, _)| *k).collect::<Vec<_>>(),
    });
    *STATE.lock().unwrap() = Some(State { engine, tele });
    env.new_string(info.to_string()).unwrap().into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeRender(mut env: JNIEnv, _c: JClass, buf: JFloatArray) {
    let n = env.get_array_length(&buf).unwrap_or(0) as usize;
    let mut out = vec![0f32; n];
    with(|s| s.engine.render(&mut out));
    let _ = env.set_float_array_region(&buf, 0, &out);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeSetParam(_e: JNIEnv, _c: JClass, index: jint, value: jdouble) {
    with(|s| s.engine.set_creature_params(&[(index as usize, value)]));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativePlayCall(_e: JNIEnv, _c: JClass, index: jint) {
    with(|s| s.engine.play_call(index as usize, None, None));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeStopCall(_e: JNIEnv, _c: JClass) {
    with(|s| s.engine.stop_call());
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeSetGate(_e: JNIEnv, _c: JClass, level: jdouble) {
    with(|s| s.engine.set_gate(level));
}

/// Telemetry as flat f64: gate, outRms, clock, active call (-1), params, tubes (areas then rms), sources.
/// Returns false if the array is too small.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeTelemetry(mut env: JNIEnv, _c: JClass, arr: JDoubleArray) -> jboolean {
    let block = with(|s| {
        if !s.engine.creature_telemetry(&mut s.tele) {
            return Vec::new();
        }
        let t = &s.tele;
        let call = match (t.call, s.engine.creature()) {
            (Some(id), Some(c)) => c.def.call_index(id).map_or(-1.0, |i| i as f64),
            _ => -1.0,
        };
        let mut b = vec![t.gate, t.out_rms, t.clock, call];
        b.extend(t.params.iter().map(|(_, v)| *v));
        for tb in &t.tubes {
            b.extend(tb.areas.iter().map(|x| *x as f64));
            b.extend(tb.rms.iter().map(|x| *x as f64));
        }
        b.extend(t.source.iter().map(|(_, v)| *v));
        b
    })
    .unwrap_or_default();
    let cap = env.get_array_length(&arr).unwrap_or(0) as usize;
    if block.is_empty() || block.len() > cap {
        return 0;
    }
    let _ = env.set_double_array_region(&arr, 0, &block);
    1
}

#[allow(dead_code)]
fn _unused(_: JString, _: jlong) {}

/// A new gas pocket (what a call's "!fill" does): a held note after a short rest otherwise
/// finds the pocket empty and stays silent.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_pukpuk_Chimera_nativeFill(_e: JNIEnv, _c: JClass) {
    with(|s| s.engine.creature_event("fill", 1.0));
}
