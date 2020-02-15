package com.tiscon.domain;

import java.io.Serializable;

public class TruckCapacity implements Serializable {
    private Integer maxBox;

    private Integer price;

    public Integer getmaxBox() {
        return maxBox;
    }

    public void setmaxBox(Integer maxBox) {
        this.maxBox = maxBox;
    }

    public Integer getprice() {
        return price;
    }

    public void setprice(Integer price) {
        this.price = price;
    }
}

