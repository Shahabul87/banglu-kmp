from perfetto.trace_processor import TraceProcessor
tp = TraceProcessor(trace="/private/tmp/claude-501/-Users-mdshahabulalam-myprojects-banlgu-banglu-kmp/57510018-3fd2-44d0-ac21-53316c2f3e0b/scratchpad/heap.pftrace")
q = tp.query("select count(*) c, min(ts) mn, max(ts) mx from heap_profile_allocation")
for r in q: print("allocation rows", r.c, "span s", (r.mx-r.mn)/1e9 if r.mx else None)
q = tp.query("""select ts, sum(case when count>0 then size else 0 end)/1048576.0 alloc_mb, sum(case when count<0 then -size else 0 end)/1048576.0 freed_mb, heap_name
from heap_profile_allocation group by ts, heap_name order by ts""")
print("--- per dump (MB): alloc, freed")
for r in q: print(round(r.ts/1e9,1), r.heap_name, round(r.alloc_mb,1), round(r.freed_mb,1))
q = tp.query("""
with a as (select callsite_id, sum(case when count>0 then size else 0 end) bytes from heap_profile_allocation group by callsite_id)
select a.bytes/1048576.0 mb, a.callsite_id from a order by a.bytes desc limit 12""")
rows = list(q)
print("--- top callsites by allocated MB")
for r in rows:
    frames = []; cid = r.callsite_id
    for _ in range(18):
        f = list(tp.query(f"select c.parent_id, coalesce(f.deobfuscated_name, f.name) name, m.name mod from stack_profile_callsite c join stack_profile_frame f on f.id=c.frame_id join stack_profile_mapping m on m.id=f.mapping where c.id={cid}"))
        if not f: break
        frames.append((f[0].name or '?')[:70] + " [" + (f[0].mod or '').split('/')[-1][:24] + "]")
        cid = f[0].parent_id
        if cid is None: break
    print(f"{r.mb:8.1f} MB  " + " <- ".join(frames[:9]))
