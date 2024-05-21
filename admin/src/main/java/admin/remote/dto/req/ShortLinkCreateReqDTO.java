package admin.remote.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ShortLinkCreateReqDTO {

    private String domain;
    private String originUrl;
    private String gid;
    private Integer createdType;
    private Integer validDateType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GTM+8")
    private Date validDate;
    private String describe;
}
