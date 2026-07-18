package com.finwatch.news.service;

import java.net.IDN;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NewsPublisherName {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "naver news", "naver_api_hub", "naver-api-hub", "naver", "finnhub");

    private static final Map<String, String> KNOWN_PUBLISHERS = knownPublishers();

    private NewsPublisherName() {
    }

    public static String resolve(String storedPublisher, String articleUrl) {
        String stored = storedPublisher == null ? "" : storedPublisher.trim();
        if (!stored.isBlank() && !PLACEHOLDERS.contains(stored.toLowerCase(Locale.ROOT))) {
            return stored;
        }

        String host = host(articleUrl);
        if (host.isBlank()) {
            return stored.isBlank() ? "언론사 정보 없음" : stored;
        }
        for (Map.Entry<String, String> entry : KNOWN_PUBLISHERS.entrySet()) {
            if (host.equals(entry.getKey()) || host.endsWith("." + entry.getKey())) {
                return entry.getValue();
            }
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private static String host(String articleUrl) {
        if (articleUrl == null || articleUrl.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(articleUrl.trim()).getHost();
            return host == null ? "" : IDN.toUnicode(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static Map<String, String> knownPublishers() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ajunews.com", "아주경제");
        values.put("breaknews.com", "브레이크뉴스");
        values.put("asiatoday.co.kr", "아시아투데이");
        values.put("cbci.co.kr", "CBC뉴스");
        values.put("kpenews.com", "한국정경신문");
        values.put("jejusori.net", "제주의소리");
        values.put("bbsi.co.kr", "BBS NEWS");
        values.put("yna.co.kr", "연합뉴스");
        values.put("yonhapnews.co.kr", "연합뉴스");
        values.put("newsis.com", "뉴시스");
        values.put("news1.kr", "뉴스1");
        values.put("mk.co.kr", "매일경제");
        values.put("hankyung.com", "한국경제");
        values.put("sedaily.com", "서울경제");
        values.put("edaily.co.kr", "이데일리");
        values.put("asiae.co.kr", "아시아경제");
        values.put("fnnews.com", "파이낸셜뉴스");
        values.put("mt.co.kr", "머니투데이");
        values.put("heraldcorp.com", "헤럴드경제");
        values.put("chosun.com", "조선일보");
        values.put("joongang.co.kr", "중앙일보");
        values.put("donga.com", "동아일보");
        values.put("hani.co.kr", "한겨레");
        values.put("khan.co.kr", "경향신문");
        values.put("etnews.com", "전자신문");
        values.put("zdnet.co.kr", "지디넷코리아");
        values.put("inews24.com", "아이뉴스24");
        values.put("ddaily.co.kr", "디지털데일리");
        values.put("businesspost.co.kr", "비즈니스포스트");
        values.put("dealsite.co.kr", "딜사이트");
        values.put("thebell.co.kr", "더벨");
        values.put("apparelnews.co.kr", "어패럴뉴스");
        values.put("betanews.net", "베타뉴스");
        values.put("beyondpost.co.kr", "비욘드포스트");
        values.put("biz.newdaily.co.kr", "뉴데일리 경제");
        values.put("biz.sbs.co.kr", "SBS Biz");
        values.put("biztribune.co.kr", "비즈트리뷴");
        values.put("busan.com", "부산일보");
        values.put("businesskorea.co.kr", "비즈니스코리아");
        values.put("ccdailynews.com", "충청일보");
        values.put("choicenews.co.kr", "초이스경제");
        values.put("coinreaders.com", "코인리더스");
        values.put("daejonilbo.com", "대전일보");
        values.put("dailian.co.kr", "데일리안");
        values.put("ddanzi.com", "딴지일보");
        values.put("delighti.co.kr", "딜라이트닷넷");
        values.put("discoverynews.kr", "디스커버리뉴스");
        values.put("dynews.co.kr", "동양일보");
        values.put("ebn.co.kr", "EBN 산업경제");
        values.put("econonews.co.kr", "이코노뉴스");
        values.put("econovill.com", "이코노믹리뷰");
        values.put("ekn.kr", "에너지경제신문");
        values.put("epnc.co.kr", "테크월드뉴스");
        values.put("ezyeconomy.com", "이지경제");
        values.put("financialpost.co.kr", "파이낸셜포스트");
        values.put("fntoday.co.kr", "파이낸스투데이");
        values.put("ftoday.co.kr", "파이낸셜투데이");
        values.put("gamevu.co.kr", "게임뷰");
        values.put("g-enews.com", "글로벌이코노믹");
        values.put("ggilbo.com", "금강일보");
        values.put("gokorea.kr", "공감신문");
        values.put("goodkyung.com", "굿모닝경제");
        values.put("gukjenews.com", "국제뉴스");
        values.put("hankookilbo.com", "한국일보");
        values.put("hansbiz.co.kr", "한스경제");
        values.put("hidomin.com", "경북도민일보");
        values.put("ichannela.com", "채널A");
        values.put("idaegu.co.kr", "대구신문");
        values.put("imaeil.com", "매일신문");
        values.put("imnews.imbc.com", "MBC 뉴스");
        values.put("incheonilbo.com", "인천일보");
        values.put("insight.co.kr", "인사이트");
        values.put("jejumaeil.net", "제주매일");
        values.put("jjan.kr", "전북일보");
        values.put("jnilbo.com", "전남일보");
        values.put("joseilbo.com", "조세일보");
        values.put("kado.net", "강원도민일보");
        values.put("kbench.com", "케이벤치");
        values.put("kjdaily.com", "광주매일신문");
        values.put("kjmbc.co.kr", "광주MBC");
        values.put("kmib.co.kr", "국민일보");
        values.put("kookje.co.kr", "국제신문");
        values.put("koreaherald.com", "코리아헤럴드");
        values.put("koreajoongangdaily.com", "코리아중앙데일리");
        values.put("kukinews.com", "쿠키뉴스");
        values.put("kyeonggi.com", "경기일보");
        values.put("kyeongin.com", "경인일보");
        values.put("lawtimes.co.kr", "법률신문");
        values.put("m.skyedaily.com", "스카이데일리");
        values.put("mediajeju.com", "미디어제주");
        values.put("m-i.kr", "매일일보");
        values.put("munhwa.com", "문화일보");
        values.put("namdonews.com", "남도일보");
        values.put("news.einfomax.co.kr", "연합인포맥스");
        values.put("news.ifm.kr", "경인방송");
        values.put("news.jtbc.co.kr", "JTBC 뉴스");
        values.put("news.kbs.co.kr", "KBS 뉴스");
        values.put("news.mtn.co.kr", "머니투데이방송");
        values.put("news.sbs.co.kr", "SBS 뉴스");
        values.put("news.tvchosun.com", "TV조선 뉴스");
        values.put("newscj.com", "천지일보");
        values.put("newsclaim.co.kr", "뉴스클레임");
        values.put("newsdream.kr", "뉴스드림");
        values.put("newsfc.co.kr", "뉴스FC");
        values.put("newspim.com", "뉴스핌");
        values.put("newsprime.co.kr", "프라임경제");
        values.put("newsroad.co.kr", "뉴스로드");
        values.put("newstown.co.kr", "뉴스타운");
        values.put("newsway.co.kr", "뉴스웨이");
        values.put("newsworks.co.kr", "뉴스웍스");
        values.put("nocutnews.co.kr", "노컷뉴스");
        values.put("obsnews.co.kr", "OBS뉴스");
        values.put("ohmynews.com", "오마이뉴스");
        values.put("pinpointnews.co.kr", "핀포인트뉴스");
        values.put("platum.kr", "플래텀");
        values.put("pointe.co.kr", "포인트데일리");
        values.put("polinews.co.kr", "폴리뉴스");
        values.put("raonnews.com", "라온신문");
        values.put("rcast.co.kr", "리얼캐스트");
        values.put("segye.com", "세계일보");
        values.put("sentv.co.kr", "서울경제TV");
        values.put("seoul.co.kr", "서울신문");
        values.put("seoulfn.com", "서울파이낸스");
        values.put("shinailbo.co.kr", "신아일보");
        values.put("slist.kr", "싱글리스트");
        values.put("srtimes.kr", "SR타임스");
        values.put("startuptoday.co.kr", "스타트업투데이");
        values.put("straightnews.co.kr", "스트레이트뉴스");
        values.put("swtvnews.com", "SWTV");
        values.put("tfmedia.co.kr", "조세금융신문");
        values.put("the-biz.co.kr", "더비즈");
        values.put("thelec.kr", "디일렉");
        values.put("thereport.co.kr", "더리포트");
        values.put("thescoop.co.kr", "더스쿠프");
        values.put("tokenpost.kr", "토큰포스트");
        values.put("topstarnews.net", "톱스타뉴스");
        values.put("tournews21.com", "투어코리아");
        values.put("ujeil.com", "울산제일일보");
        values.put("ulsanpress.net", "울산신문");
        values.put("vegannews.co.kr", "비건뉴스");
        values.put("venturesquare.net", "벤처스퀘어");
        values.put("viva100.com", "브릿지경제");
        values.put("weekly.hankooki.com", "주간한국");
        values.put("weeklytoday.com", "위클리오늘");
        values.put("wowtv.co.kr", "한국경제TV");
        values.put("yonhapnewstv.co.kr", "연합뉴스TV");
        values.put("ytn.co.kr", "YTN");
        return Map.copyOf(values);
    }
}
