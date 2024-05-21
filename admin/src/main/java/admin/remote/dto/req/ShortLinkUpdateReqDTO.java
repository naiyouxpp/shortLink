package admin.remote.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ShortLinkUpdateReqDTO {
    private String originUrl;
    private String gid;
    private Integer validDateType;
    private String fullShortUrl;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GTM+8")
    private Date validDate;
    private String describe;

}
