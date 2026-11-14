use chimera::engine::Engine;

fn stats(out: &[f32]) -> (f32, f32, usize) {
    let peak = out.iter().fold(0f32, |a, x| a.max(x.abs()));
    let rms = (out.iter().map(|x| x * x).sum::<f32>() / out.len() as f32).sqrt();
    let nan = out.iter().filter(|x| !x.is_finite()).count();
    (peak, rms, nan)
}

fn engine() -> Engine {
    let mut e = Engine::new(48000.0);
    e.set_creature(chimera::creatures::creature_by_id("flatulence"), &[]);
    e
}

#[test]
fn every_call_sounds() {
    let def = chimera::creatures::creature_by_id("flatulence").unwrap();
    for (i, c) in def.calls.iter().enumerate() {
        let mut e = engine();
        e.play_call(i, None, None);
        let mut out = vec![0f32; 48000 * 5];
        for ch in out.chunks_mut(512) { e.render(ch); }
        let (p, r, n) = stats(&out);
        println!("{:10} peak={p:.4} rms={r:.4} nonfinite={n}", c.id);
        assert!(p > 0.001 && n == 0, "{} silent or NaN", c.id);
    }
}

#[test]
fn hold_gate_sounds() {
    let mut e = engine();
    e.set_gate(1.0);
    let mut out = vec![0f32; 48000 * 2];
    for ch in out.chunks_mut(512) { e.render(ch); }
    let (p, r, n) = stats(&out);
    println!("hold peak={p:.4} rms={r:.4} nonfinite={n}");
    assert!(p > 0.001 && n == 0);
}

#[test]
fn extremes_stay_finite() {
    let def = chimera::creatures::creature_by_id("flatulence").unwrap();
    for corner in 0..2 {
        let mut e = engine();
        let vals: Vec<(usize, f64)> = def.params.iter().enumerate()
            .map(|(i, p)| (i, if corner == 0 { p.min } else { p.max })).collect();
        e.set_creature_params(&vals);
        e.set_gate(1.0);
        let mut out = vec![0f32; 48000 * 2];
        for ch in out.chunks_mut(512) { e.render(ch); }
        let (p, _, n) = stats(&out);
        println!("corner {corner} peak={p:.4} nonfinite={n}");
        assert!(n == 0 && p < 4.0);
    }
}

#[test]
fn rumble_timeline() {
    let mut e = engine();
    e.play_call(1, None, None);
    let mut out = vec![0f32; 48000 * 4];
    for ch in out.chunks_mut(512) { e.render(ch); }
    for (i, w) in out.chunks(12000).enumerate() {
        let (p, r, _) = stats(w);
        println!("t={:.2}s peak={p:.4} rms={r:.4}", i as f32 * 0.25);
    }
}

fn seg(e: &mut Engine, secs: f32) -> (f32, f32) {
    let mut out = vec![0f32; (48000.0 * secs) as usize];
    for ch in out.chunks_mut(512) { e.render(ch); }
    let (p, r, _) = stats(&out);
    (p, r)
}

#[test]
fn repeat_hold_and_calls() {
    let mut e = engine();
    for k in 0..3 {
        e.set_gate(1.0);
        let a = seg(&mut e, 1.5);
        e.set_gate(0.0);
        let b = seg(&mut e, 1.0);
        println!("hold #{k}: held peak={:.4} rms={:.4}; released peak={:.4}", a.0, a.1, b.0);
    }
    for k in 0..3 {
        e.play_call(1, None, None);
        let a = seg(&mut e, 2.5);
        println!("rumble #{k}: peak={:.4} rms={:.4}", a.0, a.1);
    }
    e.play_call(0, None, None);
    let a = seg(&mut e, 0.3);
    e.play_call(1, None, None); // interrupt a call with another
    let b = seg(&mut e, 2.0);
    println!("squeak->rumble interrupt: {:.4} / {:.4}", a.0, b.0);
}

#[test]
fn hold_timeline() {
    let mut e = engine();
    e.set_gate(1.0);
    let mut out = vec![0f32; 48000 * 6];
    for ch in out.chunks_mut(512) { e.render(ch); }
    for (i, w) in out.chunks(24000).enumerate() {
        let (p, r, _) = stats(w);
        println!("hold t={:.1}s peak={p:.4} rms={r:.4}", i as f32 * 0.5);
    }
}

#[test]
fn quick_repress_needs_fill() {
    for fill in [false, true] {
        let mut e = engine();
        e.set_gate(1.0);
        seg(&mut e, 1.0); // pocket empties
        e.set_gate(0.0);
        seg(&mut e, 0.15); // quick re-press, gate has not decayed below 0.01
        e.set_gate(1.0);
        if fill { e.creature_event("fill", 1.0); }
        let b = seg(&mut e, 1.0);
        println!("repress fill={fill}: peak={:.4} rms={:.4}", b.0, b.1);
    }
}
