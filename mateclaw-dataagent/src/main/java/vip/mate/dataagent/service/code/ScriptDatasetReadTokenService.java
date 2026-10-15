package vip.mate.dataagent.service.code;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** HMAC 短期读取令牌；令牌内容不记录到日志。 */
@Service
public class ScriptDatasetReadTokenService {
    private final byte[] secret;
    public ScriptDatasetReadTokenService(@Value("${mateclaw.runner.token-secret:change-me-in-production}") String secret) { this.secret=secret.getBytes(StandardCharsets.UTF_8); }
    public String issue(String taskId, Long workspaceId, long ttlSeconds) { long exp=Instant.now().plusSeconds(ttlSeconds).getEpochSecond(); String body=taskId+"|"+workspaceId+"|"+exp; return enc(body)+"."+enc(sign(body)); }
    public Claims verify(String token) { try { String[] p=token.split("\\.",-1); if(p.length!=2) throw new IllegalArgumentException(); String body=new String(Base64.getUrlDecoder().decode(p[0]),StandardCharsets.UTF_8); if(!constant(sign(body),Base64.getUrlDecoder().decode(p[1]))) throw new IllegalArgumentException(); String[] f=body.split("\\|",-1); long exp=Long.parseLong(f[2]); if(exp<=Instant.now().getEpochSecond()) throw new IllegalArgumentException("token expired"); return new Claims(f[0],Long.valueOf(f[1]),exp); } catch(Exception e){throw new IllegalArgumentException("invalid read token");} }
    private String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private String enc(byte[] value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    private byte[] sign(String s){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(s.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);}}
    private boolean constant(byte[] a,byte[] b){return java.security.MessageDigest.isEqual(a,b);}
    public record Claims(String taskId,Long workspaceId,long expiresAt){}
}
