package project.service.Impl;

import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import project.service.UrlTitileService;

@Service
public class UrlTitleServiceImpl implements UrlTitileService {
    @SneakyThrows
    @Override
    public String getTitleByUrl(String url) {
        Document doc = Jsoup.connect(url).get();
        return doc.title();
    }
}
